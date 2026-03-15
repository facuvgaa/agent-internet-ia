import os
from langchain_aws import ChatBedrockConverse
from dotenv import load_dotenv

load_dotenv()


def get_bedrock_model_brain():
    """Modelo con Converse API: tool calling nativo y tool_choice respetado."""
    return ChatBedrockConverse(
        model=os.getenv("AWS_SECOND_LLM", "us.anthropic.claude-sonnet-4-6"),
        region_name=os.getenv("AWS_REGION", "us-east-2"),
        temperature=0,
    )


def get_bedrock_model_master():
    """Haiku para triaje (sin tools)."""
    return ChatBedrockConverse(
        model=os.getenv("AWS_PRIMARY_LLM", "anthropic.claude-3-haiku-20240307-v1:0"),
        region_name=os.getenv("AWS_REGION", "us-east-1"),
    )