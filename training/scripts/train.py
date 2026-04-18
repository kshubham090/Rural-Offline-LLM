"""
QLoRA fine-tuning of Qwen3-14B on the Gyan dataset.

Usage:
    python scripts/train.py --config configs/train_config.yaml

Run on: 2x A100 80GB (RunPod/Vast.ai) or 1x H100 80GB
"""

import argparse
import yaml
from pathlib import Path

import torch
from datasets import load_dataset
from peft import LoraConfig, get_peft_model, TaskType
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
)
from trl import SFTTrainer, DataCollatorForCompletionOnlyLM


def load_config(path: str) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def build_bnb_config(cfg: dict) -> BitsAndBytesConfig:
    return BitsAndBytesConfig(
        load_in_4bit=cfg["load_in_4bit"],
        bnb_4bit_quant_type=cfg["bnb_4bit_quant_type"],
        bnb_4bit_compute_dtype=getattr(torch, cfg["bnb_4bit_compute_dtype"]),
        bnb_4bit_use_double_quant=cfg["bnb_4bit_use_double_quant"],
    )


def build_lora_config(cfg: dict) -> LoraConfig:
    return LoraConfig(
        r=cfg["lora_r"],
        lora_alpha=cfg["lora_alpha"],
        lora_dropout=cfg["lora_dropout"],
        target_modules=cfg["lora_target_modules"],
        task_type=TaskType.CAUSAL_LM,
        bias="none",
    )


def format_messages(example: dict) -> dict:
    """Convert messages list to a single ChatML string for SFT."""
    text = ""
    for msg in example["messages"]:
        text += f"<|im_start|>{msg['role']}\n{msg['content']}<|im_end|>\n"
    return {"text": text}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/train_config.yaml")
    args = parser.parse_args()

    cfg = load_config(args.config)
    output_dir = Path(cfg["output_dir"])
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading tokenizer: {cfg['model_name']}")
    tokenizer = AutoTokenizer.from_pretrained(
        cfg["model_name"],
        trust_remote_code=True,
        padding_side="right",
    )
    tokenizer.pad_token = tokenizer.eos_token

    print(f"Loading model: {cfg['model_name']} in 4-bit")
    model = AutoModelForCausalLM.from_pretrained(
        cfg["model_name"],
        quantization_config=build_bnb_config(cfg),
        device_map="auto",
        trust_remote_code=True,
        torch_dtype=torch.bfloat16,
        attn_implementation="flash_attention_2",
    )
    model.config.use_cache = False
    model.enable_input_require_grads()

    print("Applying LoRA adapters")
    model = get_peft_model(model, build_lora_config(cfg))
    model.print_trainable_parameters()

    print("Loading datasets")
    train_ds = load_dataset("json", data_files=cfg["dataset_path"], split="train")
    val_ds   = load_dataset("json", data_files=cfg["val_dataset_path"], split="train")

    train_ds = train_ds.map(format_messages, remove_columns=train_ds.column_names)
    val_ds   = val_ds.map(format_messages, remove_columns=val_ds.column_names)

    print(f"Train: {len(train_ds):,} | Val: {len(val_ds):,}")

    # Only compute loss on assistant responses, not on system/user tokens
    response_template = "<|im_start|>assistant\n"
    collator = DataCollatorForCompletionOnlyLM(
        response_template=response_template,
        tokenizer=tokenizer,
    )

    training_args = TrainingArguments(
        output_dir=str(output_dir),
        num_train_epochs=cfg["num_train_epochs"],
        per_device_train_batch_size=cfg["per_device_train_batch_size"],
        gradient_accumulation_steps=cfg["gradient_accumulation_steps"],
        learning_rate=cfg["learning_rate"],
        lr_scheduler_type=cfg["lr_scheduler_type"],
        warmup_ratio=cfg["warmup_ratio"],
        weight_decay=cfg["weight_decay"],
        max_grad_norm=cfg["max_grad_norm"],
        bf16=cfg["bf16"],
        fp16=cfg["fp16"],
        logging_steps=cfg["logging_steps"],
        eval_strategy="steps",
        eval_steps=cfg["eval_steps"],
        save_steps=cfg["save_steps"],
        save_total_limit=cfg["save_total_limit"],
        load_best_model_at_end=True,
        report_to=cfg["report_to"],
        run_name=cfg["run_name"],
        dataloader_num_workers=cfg["dataloader_num_workers"],
        group_by_length=True,
        ddp_find_unused_parameters=False,
    )

    trainer = SFTTrainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        tokenizer=tokenizer,
        data_collator=collator,
        max_seq_length=cfg["max_seq_length"],
        dataset_text_field="text",
    )

    print("Starting training...")
    trainer.train()

    print(f"Saving LoRA adapter → {output_dir}")
    trainer.save_model(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))
    print("Training complete.")


if __name__ == "__main__":
    main()
