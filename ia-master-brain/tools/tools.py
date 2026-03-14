import requests
from langchain.tools import tool
from dotenv import load_dotenv
import os


load_dotenv()



back_endpoint = os.getenv("BACK_API", "http://localhost:8080/api/v1/internet-ia")

@tool
def get_customer_info(customer_id: str)-> dict:
    

    url = f"{back_endpoint}/customers/{customer_id}"
    
    response = requests.get(url)
    
    return response.json()

@tool
def get_customer_service(customer_id: str)-> dict:

    url = f"{back_endpoint}/customers/services/{customer_id}"
    
    response = requests.get(url)

    return response.json()

    