from flask import Flask, jsonify, request
from flask_cors import CORS
import sqlite3, time, threading, os, signal

# --- DHT22 ---
import adafruit_dht, board
DHT_PIN = board.D4                     # GPIO4 (fizicki pin 7)
dht = adafruit_dht.DHT22(DHT_PIN, use_pulseio=False)

DB_PATH = "/home/user/dht22/measurements.db"
SAMPLE_PERIOD_SEC = 10                 # koliko cesto citamo senzor
RETENTION_DAYS = 3                     # koliko dugo cuvamo zapise

app = Flask(__name__)
CORS(app)                              # dopusta pozive s Androida
stop_flag = threading.Event()

def cpu_temp_c():
    try:
        with open('/sys/class/thermal/thermal_zone0/temp') as f:
            return round(int(f.read().strip())/1000.0, 1)
    except Exception:
        return None

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS readings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts INTEGER NOT NULL,
            temp_c REAL,
            hum_pct REAL
        );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_ts ON readings(ts);")
    conn.commit()
    conn.close()

def read_dht22():
    # do 3 pokušaja jer DHT22 ponekad baci RuntimeError
    for _ in range(3):
        try:
            t = dht.temperature
            h = dht.humidity
            if t is not None and h is not None:
                return round(float(t), 1), round(float(h), 1)
        except RuntimeError:
            time.sleep(0.5)
    return None, None

def sampler_loop():
    while not stop_flag.is_set():
        now = int(time.time())
        t, h = read_dht22()
        conn = get_db()
        try:
            conn.execute("INSERT INTO readings(ts,temp_c,hum_pct) VALUES(?,?,?)", (now, t, h))
            # retention: briši starije od RETENTION_DAYS
            cutoff = now - RETENTION_DAYS * 24 * 3600
            conn.execute("DELETE FROM readings WHERE ts < ?", (cutoff,))
            conn.commit()
        finally:
            conn.close()
        stop_flag.wait(SAMPLE_PERIOD_SEC)

@app.route("/api/health")
def health():
    return jsonify({"ok": True, "time": int(time.time())})

@app.route("/api/sensors")
def sensors():
    # zadnje očitanje iz baze (ako je prazna, pročitaj direktno)
    conn = get_db()
    cur = conn.execute("SELECT ts,temp_c,hum_pct FROM readings ORDER BY ts DESC LIMIT 1")
    row = cur.fetchone()
    conn.close()
    if row:
        return jsonify({
            "timestamp": row["ts"],
            "temp_c": row["temp_c"],
            "hum_pct": row["hum_pct"],
            "cpu_temp_c": cpu_temp_c()
        })
    else:
        t, h = read_dht22()
        return jsonify({
            "timestamp": int(time.time()),
            "temp_c": t,
            "hum_pct": h,
            "cpu_temp_c": cpu_temp_c()
        })

def _int_param(name):
    v = request.args.get(name)
    try:
        return int(v) if v is not None else None
    except:
        return None

@app.route("/api/history")
def history():
    now = int(time.time())
    minutes = _int_param("minutes")
    since = _int_param("since")
    until = _int_param("until")
    limit = _int_param("limit") or 2000  # zaštita

    if minutes:
        since = now - minutes * 60
    if not since:
        since = now - 3600  # default 60 min
    if not until:
        until = now

    conn = get_db()
    cur = conn.execute("""
        SELECT ts,temp_c,hum_pct
        FROM readings
        WHERE ts BETWEEN ? AND ?
        ORDER BY ts ASC
        LIMIT ?
    """, (since, until, limit))
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return jsonify({"from": since, "to": until, "count": len(rows), "data": rows})

def start_sampler():
    th = threading.Thread(target=sampler_loop, daemon=True)
    th.start()
    return th

def cleanup(*_):
    stop_flag.set()
    time.sleep(0.2)
    os._exit(0)

if __name__ == "__main__":
    # obrisi bazu pri svakom pokretanju (zbog problema sa satom)
    try:
        if os.path.exists(DB_PATH):
            os.remove(DB_PATH)
    except Exception as e:
        print("Ne mogu obrisati bazu:", e)

    init_db()
    signal.signal(signal.SIGINT, cleanup)
    signal.signal(signal.SIGTERM, cleanup)
    start_sampler()
    app.run(host="0.0.0.0", port=5000)   # posluži na svim sučeljima (bitno za AP)
