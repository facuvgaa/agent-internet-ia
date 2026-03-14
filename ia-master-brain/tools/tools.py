import requests
from langchain.tools import tool
from dotenv import load_dotenv
import os

load_dotenv



back_endpoint = os.getenv("BACK_API", "http://localhost:8080/api/v1/internet-ia")

@tool
def customer_info(customer_id: str)-> dict:
    
    response = requests.get(f"{back_endpoint}/customers/{customer_id}")

    data = response.json()
    return data

@tool
def customer_service(customer_id: str)-> dict:

    response = requests.get(f"{back_endpoint}/customers/services/{customer_id}")

    data = response.json()

    return data

    