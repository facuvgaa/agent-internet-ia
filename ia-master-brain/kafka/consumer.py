import os 
import json
from dotenv import load_dotenv
from confluent_kafka import Consumer, Producer, KafkaError
from langchain_aws import ChatBedrock
from langchain_core.messages import HumanMessage

load_dotenv()

