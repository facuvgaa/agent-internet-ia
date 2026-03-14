import os
from langchain_aws import ChatBedrock
from dotenv import load_dotenv

load_dotenv()


def get_bedrock_model_brain():
    
    return ChatBedrock(
        model_id=os.getenv("AWS_SECOND_LLM", "us.anthropic.claude-3-5-sonnet-20240620-v1:0"),
        region_name=os.getenv("AWS_REGION", "us-east-1"),
        model_kwargs={"temperature": 0})


def get_bedrock_model_master():

    return ChatBedrock(
        model_id = os.getenv("AWS_PRIMARY_LLM", "anthropic.claude-3-haiku-20240307-v1:0"),
        region_name= os.getenv("AWS_REGION", "us-east-1")
    )