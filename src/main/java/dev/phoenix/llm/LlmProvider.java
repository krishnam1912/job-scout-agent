package dev.phoenix.llm;

/**
 * A language model the engine can ask for judgment. The single method keeps providers interchangeable: Ollama, LM
 * Studio, OpenAI or any future API only differ behind this seam, and callers never see transport details.
 */
public interface LlmProvider {

    /** The name used in phoenix.yml. */
    String name();

    /** @throws LlmException when the model cannot be reached or never produces an acceptable reply */
    LlmResponse complete(LlmRequest request);
}
