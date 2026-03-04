from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
import os
import threading
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

# ----------------------------
# App config
# ----------------------------
ENV = os.getenv("ENV", "dev").strip().lower()  # dev | prod

app = FastAPI(
    docs_url=None if ENV == "prod" else "/docs",
    redoc_url=None if ENV == "prod" else "/redoc",
    openapi_url=None if ENV == "prod" else "/openapi.json",
)

# ----------------------------
# API models (contract invariato)
# ----------------------------
class ScoreItem(BaseModel):
    messageLocalId: int
    text: str

class ScoreRequest(BaseModel):
    conversationId: str
    modelVersion: str
    items: List[ScoreItem]

class ScoredItem(BaseModel):
    messageLocalId: int
    score: float

class ScoreResponse(BaseModel):
    modelVersion: str
    results: List[ScoredItem]

# ----------------------------
# Limits / knobs (tunable via env)
# ----------------------------
MAX_ITEMS = int(os.getenv("MAX_ITEMS", "128"))
MAX_TEXT_CHARS = int(os.getenv("MAX_TEXT_CHARS", "300"))
MAX_SEQ_LEN = int(os.getenv("MAX_SEQ_LEN", "48"))
MAX_CONV_ID_CHARS = int(os.getenv("MAX_CONV_ID_CHARS", "128"))
MAX_MODEL_VERSION_CHARS = int(os.getenv("MAX_MODEL_VERSION_CHARS", "256"))

# For ambiguous labels (LABEL_0/LABEL_1) -> you MUST set TOXIC_CLASS_INDEX (0/1)
FORCE_INDEX = os.getenv("TOXIC_CLASS_INDEX")

# ----------------------------
# Model paths / tokenizer
# ----------------------------
MODEL_ID = os.getenv("TOXIC_MODEL_ID", "textdetox/xlmr-large-toxicity-classifier-v2")

ONNX_PATH = os.getenv("ONNX_MODEL_PATH", r"onnx_out\model.onnx")
if not os.path.exists(ONNX_PATH):
    raise RuntimeError(f"ONNX model not found at {ONNX_PATH}. Set ONNX_MODEL_PATH.")

# Tokenizer (HF)
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)

# ----------------------------
# ONNX Runtime session (CPU)
# ----------------------------
providers = ["CPUExecutionProvider"]

INTRA_OP = int(os.getenv("ORT_INTRA_OP_NUM_THREADS", "4"))
INTER_OP = int(os.getenv("ORT_INTER_OP_NUM_THREADS", "1"))

sess_opts = ort.SessionOptions()
sess_opts.intra_op_num_threads = INTRA_OP
sess_opts.inter_op_num_threads = INTER_OP
sess_opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

session = ort.InferenceSession(ONNX_PATH, sess_options=sess_opts, providers=providers)

input_names = [i.name for i in session.get_inputs()]
output_names = [o.name for o in session.get_outputs()]
HAS_TOKEN_TYPE = "token_type_ids" in input_names

# Avoid bad contention if multiple requests hit the server
INFER_LOCK = threading.Lock()

# ----------------------------
# Math helpers
# ----------------------------
def sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))

def softmax(x: np.ndarray, axis: int = -1) -> np.ndarray:
    x = x - np.max(x, axis=axis, keepdims=True)
    e = np.exp(x)
    return e / np.sum(e, axis=axis, keepdims=True)

def resolve_toxic_index(num_labels: int) -> int:
    """
    In ONNX-only mode we cannot reliably map labels (id2label).
    For your binary LABEL_0/LABEL_1 models: set TOXIC_CLASS_INDEX=0 or 1.
    """
    if FORCE_INDEX is None or FORCE_INDEX == "":
        if num_labels == 2:
            # Default guess: class 1 = toxic (common). If inverted, set TOXIC_CLASS_INDEX=0.
            return 1
        raise RuntimeError(
            "TOXIC_CLASS_INDEX is required for non-binary models in ONNX-only mode."
        )
    try:
        idx = int(FORCE_INDEX)
        if idx < 0 or idx >= num_labels:
            raise ValueError
        return idx
    except ValueError:
        raise RuntimeError(f"TOXIC_CLASS_INDEX non valido: {FORCE_INDEX}")

@app.get("/health")
def health():
    return {
        "ok": True,
        "modelVersion": MODEL_ID,
        "onnxPath": ONNX_PATH,
        "providers": session.get_providers(),
        "inputs": input_names,
        "outputs": output_names,
        "ortThreads": {"intra": INTRA_OP, "inter": INTER_OP},
        "maxSeqLen": MAX_SEQ_LEN,
        "maxTextChars": MAX_TEXT_CHARS,
        "maxItems": MAX_ITEMS,
        "hasTokenTypeIds": HAS_TOKEN_TYPE,
    }

@app.post("/toxicity/score", response_model=ScoreResponse)
def score(req: ScoreRequest):
    if not req.items:
        return ScoreResponse(modelVersion=MODEL_ID, results=[])

    # Input validation
    if req.conversationId and len(req.conversationId) > MAX_CONV_ID_CHARS:
        raise HTTPException(status_code=413, detail="conversationId too long")

    if req.modelVersion and len(req.modelVersion) > MAX_MODEL_VERSION_CHARS:
        raise HTTPException(status_code=413, detail="modelVersion too long")

    # Strict modelVersion check (keep if you want traceability)
    if req.modelVersion and req.modelVersion != MODEL_ID:
        raise HTTPException(
            status_code=400,
            detail=f"modelVersion richiesto ({req.modelVersion}) diverso dal modello caricato ({MODEL_ID})."
        )

    if len(req.items) > MAX_ITEMS:
        raise HTTPException(status_code=413, detail=f"Too many items (max {MAX_ITEMS}).")

    texts = [(it.text or "")[:MAX_TEXT_CHARS] for it in req.items]

    # Tokenize -> numpy int64
    enc = tokenizer(
        texts,
        padding=True,
        truncation=True,
        max_length=MAX_SEQ_LEN,
        return_tensors="np"
    )

    ort_inputs = {
        "input_ids": enc["input_ids"].astype(np.int64),
        "attention_mask": enc["attention_mask"].astype(np.int64),
    }
    if HAS_TOKEN_TYPE and "token_type_ids" in enc:
        ort_inputs["token_type_ids"] = enc["token_type_ids"].astype(np.int64)

    with INFER_LOCK:
        outputs = session.run(None, ort_inputs)

    logits = np.asarray(outputs[0])

    # logits shape: [B], [B,1], [B,L]
    if logits.ndim == 1:
        scores = sigmoid(logits).reshape(-1)
    elif logits.shape[-1] == 1:
        scores = sigmoid(logits).reshape(-1)
    else:
        num_labels = int(logits.shape[-1])
        tox_idx = resolve_toxic_index(num_labels)

        if num_labels == 2:
            probs = softmax(logits, axis=-1)
            scores = probs[:, tox_idx]
        else:
            # Heuristic: assume multilabel if >2 labels
            probs = sigmoid(logits)
            scores = probs[:, tox_idx]

    scores_list = scores.astype(np.float32).tolist()

    results = [
        ScoredItem(messageLocalId=it.messageLocalId, score=float(s))
        for it, s in zip(req.items, scores_list)
    ]
    return ScoreResponse(modelVersion=MODEL_ID, results=results)