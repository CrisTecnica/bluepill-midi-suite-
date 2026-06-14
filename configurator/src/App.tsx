import { useCallback, useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

const NUM_PADS = 8;
const ADC_MAX = 4095;

interface Config {
  th: number[];
  note: number[];
  gamma: number; // x100 (60 = 0.60)
  mask: number;
  scan: number;
  len: number;
  pmax: number;
  ch: number;
}

interface Hit {
  peak: number;
  vel: number;
}

const NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const GM: Record<number, string> = {
  35: "Kick 2", 36: "Kick", 37: "Side Stick", 38: "Snare", 39: "Clap",
  40: "Snare 2", 41: "Tom F2", 42: "HH Fech", 43: "Tom F", 44: "HH Pedal",
  45: "Tom M", 46: "HH Aberto", 47: "Tom M2", 48: "Tom A", 49: "Crash",
  50: "Tom A2", 51: "Ride", 52: "China", 53: "Ride Bell", 55: "Splash",
  57: "Crash 2", 59: "Ride 2",
};

function noteLabel(n: number) {
  const name = `${NOTE_NAMES[n % 12]}${Math.floor(n / 12) - 1}`;
  return GM[n] ? `${name} · ${GM[n]}` : name;
}

export default function App() {
  const [ports, setPorts] = useState<string[]>([]);
  const [port, setPort] = useState("");
  const [connected, setConnected] = useState(false);
  const [cfg, setCfg] = useState<Config | null>(null);
  const [hits, setHits] = useState<(Hit | null)[]>(Array(NUM_PADS).fill(null));
  const [meters, setMeters] = useState<number[]>(Array(NUM_PADS).fill(0));
  const [flash, setFlash] = useState<boolean[]>(Array(NUM_PADS).fill(false));
  const [toast, setToast] = useState("");

  const meterTimers = useRef<number[]>([]);
  const flashTimers = useRef<number[]>([]);
  const debounceTimers = useRef<Record<string, number>>({});
  const toastTimer = useRef<number>(0);
  const cfgReceived = useRef(false);
  const getRetryTimer = useRef<number>(0);
  const monRetryTimer = useRef<number>(0);

  const showToast = useCallback((msg: string, duration = 2500) => {
    setToast(msg);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(""), duration);
  }, []);

  const refreshPorts = useCallback(async () => {
    try {
      setPorts(await invoke<string[]>("list_ports"));
    } catch {
      setPorts([]);
    }
  }, []);

  useEffect(() => {
    refreshPorts();
  }, [refreshPorts]);

  useEffect(() => {
    const unLine = listen<string>("serial-line", (ev) => {
      let obj: any;
      try {
        obj = JSON.parse(ev.payload);
      } catch {
        return;
      }

      if (Array.isArray(obj.th)) {
        cfgReceived.current = true;
        window.clearTimeout(getRetryTimer.current);
        setCfg(obj as Config);
        return;
      }

      if (obj.e === "hit" && typeof obj.p === "number") {
        const p = obj.p as number;
        if (p < 0 || p >= NUM_PADS) return;
        setHits((h) => {
          const n = [...h];
          n[p] = { peak: obj.peak, vel: obj.vel };
          return n;
        });
        setMeters((m) => {
          const n = [...m];
          n[p] = Math.min(100, (obj.peak / ADC_MAX) * 100);
          return n;
        });
        setFlash((f) => {
          const n = [...f];
          n[p] = true;
          return n;
        });
        window.clearTimeout(meterTimers.current[p]);
        meterTimers.current[p] = window.setTimeout(() => {
          setMeters((m) => {
            const n = [...m];
            n[p] = 0;
            return n;
          });
        }, 140);
        window.clearTimeout(flashTimers.current[p]);
        flashTimers.current[p] = window.setTimeout(() => {
          setFlash((f) => {
            const n = [...f];
            n[p] = false;
            return n;
          });
        }, 220);
        return;
      }

      if (obj.ok === "saved") {
        showToast("Configuração salva na flash da placa");
      }
      if (obj.err === "flash") {
        showToast("Erro: falha ao gravar na flash — tente novamente", 4000);
      }
    });

    const unClosed = listen("serial-closed", () => {
      setConnected(false);
      setCfg(null);
      setHits(Array(NUM_PADS).fill(null));
      setMeters(Array(NUM_PADS).fill(0));
      setFlash(Array(NUM_PADS).fill(false));
    });

    return () => {
      unLine.then((f) => f());
      unClosed.then((f) => f());
      window.clearTimeout(getRetryTimer.current);
      window.clearTimeout(monRetryTimer.current);
    };
  }, []);

  const send = useCallback((line: string) => {
    invoke("send_line", { line }).catch(() => {});
  }, []);

  const connect = async () => {
    if (!port) return;
    try {
      await invoke("connect_port", { port });
      cfgReceived.current = false;
      setConnected(true);
      send("MON 1");
      send("GET");
      window.clearTimeout(getRetryTimer.current);
      getRetryTimer.current = window.setTimeout(() => {
        if (!cfgReceived.current) send("GET");
      }, 800);
      window.clearTimeout(monRetryTimer.current);
      monRetryTimer.current = window.setTimeout(() => send("MON 1"), 2000);
    } catch (e) {
      showToast(`Falha ao conectar: ${e}`, 3500);
    }
  };

  const disconnect = async () => {
    window.clearTimeout(getRetryTimer.current);
    send("MON 0");
    await invoke("disconnect_port").catch(() => {});
    setConnected(false);
    setCfg(null);
    setHits(Array(NUM_PADS).fill(null));
    setMeters(Array(NUM_PADS).fill(0));
    setFlash(Array(NUM_PADS).fill(false));
  };

  /** Atualiza o estado local e envia SET com debounce de 120 ms. */
  const setParam = (key: string, idx: number, val: number, apply: (c: Config) => Config) => {
    setCfg((c) => (c ? apply(c) : c));
    const k = `${key}:${idx}`;
    window.clearTimeout(debounceTimers.current[k]);
    debounceTimers.current[k] = window.setTimeout(
      () => send(`SET ${key} ${idx} ${val}`),
      120
    );
  };

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-dot" data-on={connected} />
          <h1>BLUEPILL · MIDI TRIGGER</h1>
        </div>

        <div className="topbar-controls">
          <select value={port} onChange={(e) => setPort(e.target.value)} disabled={connected}>
            <option value="">Porta serial…</option>
            {ports.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>
          <button className="ghost" onClick={refreshPorts} disabled={connected} title="Atualizar portas">
            ⟳
          </button>
          {connected ? (
            <button className="ghost" onClick={disconnect}>Desconectar</button>
          ) : (
            <button className="primary" onClick={connect} disabled={!port}>Conectar</button>
          )}
          <button className="primary" onClick={async () => {
              try {
                await invoke<void>("send_line", { line: "SAVE" });
              } catch (e) {
                showToast(`Erro ao salvar: ${e}`, 3500);
              }
            }} disabled={!connected || !cfg}>
            Salvar na placa
          </button>
        </div>
      </header>

      {!connected && (
        <div className="empty">
          <p>Conecte a placa via USB e selecione a porta serial (ex.: /dev/ttyACM0).</p>
          <p className="dim">A porta MIDI continua livre para o sampler de bateria — os dois funcionam em paralelo.</p>
        </div>
      )}

      {connected && !cfg && <div className="empty"><p>Lendo configuração da placa…</p></div>}

      {connected && cfg && (
        <>
          <main className="pads">
            {Array.from({ length: NUM_PADS }, (_, i) => {
              const thPct = (cfg.th[i] / ADC_MAX) * 100;
              const pmaxPct = (cfg.pmax / ADC_MAX) * 100;
              const hit = hits[i];
              return (
                <section className="pad" key={i} data-hit={flash[i]}>
                  <div className="pad-head">
                    <span className="pad-led" data-on={flash[i]} />
                    <span className="pad-name">PAD {i + 1}</span>
                    <span className="pad-vel">{hit ? `v${hit.vel}` : "—"}</span>
                  </div>

                  <div className="meter">
                    <div className="meter-fill" style={{ height: `${meters[i]}%` }} />
                    <div className="meter-tick th" style={{ bottom: `${thPct}%` }} title="Threshold" />
                    <div className="meter-tick pmax" style={{ bottom: `${pmaxPct}%` }} title="Pico máx" />
                  </div>

                  <div className="pad-last">
                    pico <b>{hit ? hit.peak : "—"}</b>
                  </div>

                  <label className="field">
                    <span>LIMIAR <b>{cfg.th[i]}</b></span>
                    <input
                      type="range" min={0} max={4095} value={cfg.th[i]}
                      onChange={(e) => {
                        const v = Number(e.target.value);
                        setParam("TH", i, v, (c) => {
                          const th = [...c.th]; th[i] = v; return { ...c, th };
                        });
                      }}
                    />
                  </label>

                  <label className="field">
                    <span>NOTA <b className="note-name">{noteLabel(cfg.note[i])}</b></span>
                    <input
                      type="number" min={0} max={127} value={cfg.note[i]}
                      onChange={(e) => {
                        const v = Math.max(0, Math.min(127, Number(e.target.value)));
                        setParam("NOTE", i, v, (c) => {
                          const note = [...c.note]; note[i] = v; return { ...c, note };
                        });
                      }}
                    />
                  </label>
                </section>
              );
            })}
          </main>

          <footer className="master">
            <span className="master-title">MASTER</span>

            <label className="field">
              <span>CURVA <b>{(cfg.gamma / 100).toFixed(2)}</b></span>
              <input
                type="range" min={10} max={300} value={cfg.gamma}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setParam("GAMMA", 0, v, (c) => ({ ...c, gamma: v }));
                }}
              />
            </label>

            <label className="field">
              <span>MÁSCARA <b>{cfg.mask} ms</b></span>
              <input
                type="range" min={1} max={1000} value={cfg.mask}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setParam("MASK", 0, v, (c) => ({ ...c, mask: v }));
                }}
              />
            </label>

            <label className="field">
              <span>JANELA <b>{cfg.scan} µs</b></span>
              <input
                type="range" min={200} max={20000} step={100} value={cfg.scan}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setParam("SCAN", 0, v, (c) => ({ ...c, scan: v }));
                }}
              />
            </label>

            <label className="field">
              <span>NOTA OFF <b>{cfg.len} ms</b></span>
              <input
                type="range" min={5} max={2000} value={cfg.len}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setParam("LEN", 0, v, (c) => ({ ...c, len: v }));
                }}
              />
            </label>

            <label className="field">
              <span>PICO MÁX <b>{cfg.pmax}</b></span>
              <input
                type="range" min={200} max={4095} step={5} value={cfg.pmax}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setParam("PMAX", 0, v, (c) => ({ ...c, pmax: v }));
                }}
              />
            </label>

            <label className="field">
              <span>CANAL MIDI <b>{cfg.ch + 1}</b></span>
              <input
                type="range" min={0} max={15} value={cfg.ch}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setParam("CH", 0, v, (c) => ({ ...c, ch: v }));
                }}
              />
            </label>
          </footer>
        </>
      )}

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
