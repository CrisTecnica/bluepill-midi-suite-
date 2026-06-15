// Evita janela de console extra no Windows em release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serialport::SerialPort;
use std::io::{BufRead, BufReader, Write};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc, Mutex,
};
use std::time::Duration;
use tauri::{AppHandle, Emitter, State};

#[derive(Default)]
struct SerialState {
    writer: Mutex<Option<Box<dyn SerialPort>>>,
    stop: Mutex<Option<Arc<AtomicBool>>>,
}

#[tauri::command]
fn list_ports() -> Vec<String> {
    serialport::available_ports()
        .map(|ports| ports.into_iter().map(|p| p.port_name).collect())
        .unwrap_or_default()
}

#[tauri::command]
fn disconnect_port(state: State<'_, SerialState>) -> Result<(), String> {
    if let Some(stop) = state.stop.lock().unwrap().take() {
        stop.store(true, Ordering::Relaxed);
    }
    *state.writer.lock().unwrap() = None;
    Ok(())
}

#[tauri::command]
fn connect_port(
    app: AppHandle,
    state: State<'_, SerialState>,
    port: String,
) -> Result<(), String> {
    // Encerra conexão anterior, se houver
    if let Some(stop) = state.stop.lock().unwrap().take() {
        stop.store(true, Ordering::Relaxed);
    }
    *state.writer.lock().unwrap() = None;

    // No Windows o cdc_acm não assert DTR automaticamente; o STM32
    // (USBCompositeSerial) só processa dados quando DTR está alto, então
    // precisamos pedi-lo explicitamente. No Linux o kernel já faz isso
    // durante o open() — aplicar dtr_on_open lá causaria um segundo
    // SET_CONTROL_LINE_STATE que pode confundir o firmware.
    let builder = serialport::new(&port, 115_200)
        .timeout(Duration::from_millis(100))
        .data_bits(serialport::DataBits::Eight)
        .stop_bits(serialport::StopBits::One)
        .parity(serialport::Parity::None)
        .flow_control(serialport::FlowControl::None);

    #[cfg(target_os = "windows")]
    let builder = builder.dtr_on_open(true);

    let sp = builder.open().map_err(|e| e.to_string())?;

    let reader_sp = sp.try_clone().map_err(|e| e.to_string())?;
    let stop = Arc::new(AtomicBool::new(false));

    *state.writer.lock().unwrap() = Some(sp);
    *state.stop.lock().unwrap() = Some(stop.clone());

    std::thread::spawn(move || {
        let mut reader = BufReader::new(reader_sp);
        let mut line = String::new();
        let mut last_error: Option<String> = None;
        while !stop.load(Ordering::Relaxed) {
            line.clear();
            match reader.read_line(&mut line) {
                Ok(0) => break,
                Ok(_) => {
                    let trimmed = line.trim();
                    if !trimmed.is_empty() {
                        let _ = app.emit("serial-line", trimmed.to_string());
                    }
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut => continue,
                Err(e) => {
                    last_error = Some(e.to_string());
                    break;
                }
            }
        }
        if !stop.load(Ordering::Relaxed) {
            if let Some(err) = last_error {
                let _ = app.emit("serial-error", err);
            }
            let _ = app.emit("serial-closed", ());
        }
    });

    Ok(())
}

#[tauri::command]
fn send_line(state: State<'_, SerialState>, line: String) -> Result<(), String> {
    let mut guard = state.writer.lock().unwrap();
    match guard.as_mut() {
        Some(w) => w
            .write_all(line.as_bytes())
            .and_then(|_| w.write_all(b"\n"))
            .and_then(|_| w.flush())
            .map_err(|e| e.to_string()),
        None => Err("not connected".into()),
    }
}

fn main() {
    tauri::Builder::default()
        .manage(SerialState::default())
        .invoke_handler(tauri::generate_handler![
            list_ports,
            connect_port,
            disconnect_port,
            send_line
        ])
        .run(tauri::generate_context!())
        .expect("erro ao iniciar a aplicação Tauri");
}
