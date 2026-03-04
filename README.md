# ToxicChat — Android App + Toxicity Server (Python)

Questo progetto è composto da:
- **Android app**: importa chat (es. WhatsApp .txt), mostra messaggi e risultati.
- **Python server** (`toxic_server/`): espone una API HTTP per calcolare uno **score di tossicità** su batch di messaggi.

> Nota: l’app Android è configurata per chiamare il server a `http://10.0.2.2:8000/`.
> `10.0.2.2` funziona **solo** se l’app gira su **Android Emulator** (è l’host PC visto dall’emulatore).

---

## Struttura cartelle

```text
AndroidApp/
  app/                  # sorgenti Android
  toxic_server/          # server Python (FastAPI)
    main.py
    requirements.txt
    onnx_out/            # NON incluso nel repo: va scaricato/generato (vedi sotto)
      model.onnx
      model.onnx.data
      config.json
      tokenizer.json
      tokenizer_config.json
      special_tokens_map.json
```

---

## Requisiti

### Server
- Python **3.10+**
- Internet al primo avvio (per scaricare/generare i file ONNX e i file tokenizer se mancanti)

### App
- Android Studio
- Android Emulator (AVD)

---

## 0) Preparazione modello ONNX (OBBLIGATORIA)

Il server richiede i file ONNX in `toxic_server/onnx_out/`.
Per evitare di versionare file enormi nel repo, il modello va **scaricato** o **generato** sul PC del prof.

### Opzione A (consigliata): scaricare ONNX già pronto da Hugging Face

Questa opzione scarica un export già pronto che include:
- `model.onnx`
- `model.onnx.data` (external data)
- file tokenizer/config necessari

#### Windows (PowerShell) — dentro `AndroidApp/toxic_server`
```powershell
cd toxic_server
mkdir onnx_out -Force

py -m pip install -U huggingface_hub

py -c "from huggingface_hub import snapshot_download; snapshot_download(repo_id='joaopn/xlmr-large-toxicity-classifier-v2-onnx-fp16', local_dir='onnx_out', local_dir_use_symlinks=False)"
```

#### macOS / Linux — dentro `AndroidApp/toxic_server`
```bash
cd toxic_server
mkdir -p onnx_out

python3 -m pip install -U huggingface_hub

python3 -c "from huggingface_hub import snapshot_download; snapshot_download(repo_id='joaopn/xlmr-large-toxicity-classifier-v2-onnx-fp16', local_dir='onnx_out', local_dir_use_symlinks=False)"
```

---

### Opzione B: generare l’ONNX localmente dal modello Hugging Face (export)

> Questa opzione è utile se non si vuole usare un export preconfezionato.
> Richiede dipendenze aggiuntive (Optimum).

#### Windows / macOS / Linux — dentro `AndroidApp/toxic_server`
```bash
cd toxic_server
python -m venv .venv
# Windows: .\.venv\Scripts\Activate.ps1
# macOS/Linux: source .venv/bin/activate

pip install -U pip

# Runtime deps del server (presenti in requirements.txt)
pip install -r requirements.txt

# Dipendenze per export ONNX
pip install "optimum[onnx]" onnx

# Export del modello in onnx_out/
optimum-cli export onnx --model textdetox/xlmr-large-toxicity-classifier-v2 --task text-classification --sequence_length 48 onnx_out/
```

> Nota: l’export può generare un file di external data (es. `model.onnx.data`). Deve rimanere nella stessa cartella di `model.onnx`.

---

## 1) Avvio server (Python)

Aprire un terminale nella root del progetto e spostarsi in `toxic_server/`.

### Windows (PowerShell)
```powershell
cd toxic_server
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -U pip
pip install -r requirements.txt

# (Opzionale) se il modello non è in onnx_out/model.onnx:
# $env:ONNX_MODEL_PATH="onnx_out\model.onnx"

# Importante: deve combaciare con modelVersion mandato dall'app
$env:TOXIC_MODEL_ID="textdetox/xlmr-large-toxicity-classifier-v2"

uvicorn main:app --host 0.0.0.0 --port 8000
```

### macOS / Linux
```bash
cd toxic_server
python3 -m venv .venv
source .venv/bin/activate
pip install -U pip
pip install -r requirements.txt

# (Opzionale) se il modello non è in onnx_out/model.onnx:
# export ONNX_MODEL_PATH="onnx_out/model.onnx"

# Importante: deve combaciare con modelVersion mandato dall'app
export TOXIC_MODEL_ID="textdetox/xlmr-large-toxicity-classifier-v2"

uvicorn main:app --host 0.0.0.0 --port 8000
```

### Verifica server
Aprire nel browser:
- `http://127.0.0.1:8000/health`

Se risponde con stato “ok” (e info sul modello), il server è operativo.

---

## 2) Avvio app Android

1. Aprire `AndroidApp/` con Android Studio (cartella che contiene `settings.gradle(.kts)`).
2. Attendere la sincronizzazione Gradle.
3. Avviare un **Android Emulator** (AVD).
4. Premere **Run**.

> Se si avvia l’app su **telefono fisico**, `10.0.2.2` NON funziona.
> In quel caso serve usare l’IP del PC nella rete (es. `http://192.168.x.y:8000/`) nella configurazione dell’app.

---

## API del server

### Health check
- `GET /health`

### Scoring tossicità
- `POST /toxicity/score`

Esempio request:
```json
{
  "conversationId": "any-string-or-uuid",
  "modelVersion": "textdetox/xlmr-large-toxicity-classifier-v2",
  "items": [
    { "messageLocalId": 1, "text": "ciao" },
    { "messageLocalId": 2, "text": "sei uno schifo" }
  ]
}
```

Esempio response:
```json
{
  "modelVersion": "textdetox/xlmr-large-toxicity-classifier-v2",
  "results": [
    { "messageLocalId": 1, "score": 0.02 },
    { "messageLocalId": 2, "score": 0.91 }
  ]
}
```

---

## Configurazione server (variabili d’ambiente)

Le variabili sono lette da `toxic_server/main.py`.

### Modello ONNX
Di default cerca:
- `toxic_server/onnx_out/model.onnx`

Se il modello è altrove:
- `ONNX_MODEL_PATH=/percorso/al/model.onnx`

### Identificativo modello (deve combaciare con l’app)
- `TOXIC_MODEL_ID` (default: `textdetox/xlmr-large-toxicity-classifier-v2`)

Se `modelVersion` della request è diverso da `TOXIC_MODEL_ID`, il server risponde `400`.

### Classe “toxic” (solo se necessario)
- `TOXIC_CLASS_INDEX=0` oppure `TOXIC_CLASS_INDEX=1`

### Limiti e performance (opzionali)
- `MAX_ITEMS` (default 128)
- `MAX_TEXT_CHARS` (default 300)
- `MAX_SEQ_LEN` (default 48)
- `ORT_INTRA_OP_NUM_THREADS` (default 4)
- `ORT_INTER_OP_NUM_THREADS` (default 1)

### Modalità runtime
- `ENV=dev|prod`
In `prod` la documentazione OpenAPI (`/docs`) è disabilitata.

---

## Troubleshooting

### Server non parte: “ONNX model not found”
- manca `onnx_out/model.onnx` oppure `ONNX_MODEL_PATH` è sbagliata.
- verificare che, se presente, `model.onnx.data` sia nella stessa cartella di `model.onnx`.

### L’app riceve 400 “modelVersion mismatch”
- `modelVersion` nella request != `TOXIC_MODEL_ID` lato server.
- assicurarsi che `TOXIC_MODEL_ID="textdetox/xlmr-large-toxicity-classifier-v2"`.

### Funziona su emulatore ma non su telefono fisico
- `10.0.2.2` è speciale dell’emulatore: su device serve l’IP del PC e firewall aperto sulla porta 8000.

### Score invertiti (tossico/non tossico)
- provare `TOXIC_CLASS_INDEX=0` o `1` e verificare su frasi note.
