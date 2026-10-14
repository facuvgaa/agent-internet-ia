import os
import logging
from typing import Any

import boto3

logger = logging.getLogger(__name__)


class BedrockClient:
    """
    Cliente boto3 directo para AWS Bedrock Converse API.
    Reemplaza langchain-aws.ChatBedrockConverse sin dependencia de LangChain.
    """

    def __init__(self):
        self.client = boto3.client(
            "bedrock-runtime",
            region_name=os.getenv("AWS_REGION", "us-east-1"),
        )
        self.model_brain = os.getenv(
            "AWS_SECOND_LLM", "us.anthropic.claude-sonnet-4-6"
        )
        self.model_haiku = os.getenv(
            "AWS_PRIMARY_LLM", "anthropic.claude-3-haiku-20240307-v1:0"
        )

    def invoke_simple(self, prompt: str, model: str | None = None) -> str:
        """
        Llamada simple sin tools. Retorna el texto de la respuesta.
        Usada para triage, routing y routers intermedios.
        """
        model_id = model or self.model_haiku
        response = self.client.converse(
            modelId=model_id,
            messages=[{"role": "user", "content": [{"text": prompt}]}],
            inferenceConfig={"maxTokens": 200, "temperature": 0},
        )
        content = response["output"]["message"]["content"]
        return "".join(b["text"] for b in content if "text" in b)

    def invoke_with_tools(
        self,
        messages: list[dict],
        system_prompt: str,
        tools: list[dict],
        model: str | None = None,
    ) -> dict:
        """
        Llamada con tools. Retorna la respuesta completa de Bedrock.
        El caller decide si hay tool_use y ejecuta el loop.
        """
        model_id = model or self.model_brain
        kwargs: dict[str, Any] = {
            "modelId": model_id,
            "messages": messages,
            "system": [{"text": system_prompt}],
            "inferenceConfig": {"maxTokens": 4096, "temperature": 0},
        }
        if tools:
            kwargs["toolConfig"] = {"tools": tools}

        response = self.client.converse(**kwargs)
        return response

    def run_agent_loop(
        self,
        messages: list[dict],
        system_prompt: str,
        tools: list[dict],
        tool_executor,  # callable: (name, input) -> str
        max_iterations: int = 10,
    ) -> tuple[list[dict], str]:
        """
        Ejecuta el loop completo de tool-use hasta que el modelo responda
        con end_turn o se alcance el límite de iteraciones.

        Returns:
            (messages_history, final_text_response)
        """
        current_messages = list(messages)

        for iteration in range(max_iterations):
            response = self.invoke_with_tools(current_messages, system_prompt, tools)
            stop_reason = response["stopReason"]
            output_message = response["output"]["message"]

            # Agregar respuesta del asistente al historial
            current_messages.append(output_message)

            if stop_reason == "end_turn":
                final_text = "".join(
                    b["text"] for b in output_message["content"] if "text" in b
                )
                logger.info("[Bedrock] end_turn en iteración %d", iteration)
                return current_messages, final_text

            if stop_reason == "tool_use":
                tool_results = []
                for block in output_message["content"]:
                    if "toolUse" not in block:
                        continue
                    tool_call = block["toolUse"]
                    tool_name = tool_call["name"]
                    tool_input = tool_call["input"]
                    tool_use_id = tool_call["toolUseId"]

                    logger.info("[Bedrock] tool_use: %s(%s)", tool_name, tool_input)
                    try:
                        result_text = tool_executor(tool_name, tool_input)
                        tool_results.append({
                            "toolResult": {
                                "toolUseId": tool_use_id,
                                "content": [{"text": result_text}],
                                "status": "success",
                            }
                        })
                    except Exception as e:
                        logger.error("[Bedrock] tool %s failed: %s", tool_name, e)
                        tool_results.append({
                            "toolResult": {
                                "toolUseId": tool_use_id,
                                "content": [{"text": f"Error: {e}"}],
                                "status": "error",
                            }
                        })

                # Agregar resultados al historial para la próxima iteración
                current_messages.append({"role": "user", "content": tool_results})
                continue

            # stop_reason inesperado (max_tokens, guardrail, etc.)
            logger.warning("[Bedrock] stop_reason inesperado: %s", stop_reason)
            partial_text = "".join(
                b["text"] for b in output_message["content"] if "text" in b
            )
            return current_messages, partial_text or "Sin respuesta."

        logger.error("[Bedrock] Se alcanzó max_iterations=%d", max_iterations)
        return current_messages, "No pude completar la tarea. Intentá de nuevo."
