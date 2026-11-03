package com.duro.kukie.modelcatalog.domain

/**
 * 생성 모델 표의 아키텍처 목록. https://docs.vllm.ai/en/latest/models/supported_models.html 스냅숏이고 vLLM 을 실행해 본 값이 아니다.
 * 갱신할 때는 그 문서의 텍스트 생성·멀티모달 생성 표에서 아키텍처 열을 옮기고 SNAPSHOT_DATE 를 바꾼다.
 */
object VllmArchitectures {

    const val SNAPSHOT_DATE = "2026-11-03"

    val UPSTREAM: Set<String> = setOf(
        "AXK1ForCausalLM", "AfmoeForCausalLM", "AliceAIForCausalLM", "ApertusForCausalLM", "ArceeForCausalLM",
        "AriaForConditionalGeneration", "AudioFlamingo3ForConditionalGeneration", "BagelForConditionalGeneration",
        "BailingMoeForCausalLM", "BailingMoeV2ForCausalLM", "BailingMoeV2_5ForCausalLM", "BailingMoeV3ForCausalLM",
        "BailingMoeV3VLForConditionalGeneration", "BeamForCausalLM", "BeeForConditionalGeneration",
        "Blip2ForConditionalGeneration", "BloomForCausalLM", "ChatGLMForConditionalGeneration", "ChatGLMModel",
        "Cohere2ForCausalLM", "Cohere2MoeForCausalLM", "Cohere2VisionForConditionalGeneration", "CohereForCausalLM",
        "Cosmos3EdgeForConditionalGeneration", "Cosmos3ForConditionalGeneration", "CwmForCausalLM", "DbrxForCausalLM",
        "DeciLMForCausalLM", "DeepseekForCausalLM", "DeepseekOCR2ForCausalLM", "DeepseekOCRForCausalLM",
        "DeepseekV2ForCausalLM", "DeepseekV32ForCausalLM", "DeepseekV3ForCausalLM", "DeepseekV4ForCausalLM",
        "DeepseekV4ForConditionalGeneration", "DeepseekVLV2ForCausalLM", "DomynEdgeForCausalLM", "DoryForCausalLM",
        "DotsOCRForCausalLM", "Eagle2_5_VLForConditionalGeneration", "Emu3ForConditionalGeneration",
        "Ernie4_5ForCausalLM", "Ernie4_5_MoeForCausalLM", "Ernie4_5_VLMoeForConditionalGeneration",
        "Exaone4ForCausalLM", "Exaone4_5_ForConditionalGeneration", "ExaoneForCausalLM", "ExaoneMoeForCausalLM",
        "FalconForCausalLM", "FalconH1ForCausalLM", "FalconMambaForCausalLM", "FlexOlmoForCausalLM",
        "FlintForCausalLM", "GLM4VForCausalLM", "GPT2LMHeadModel", "GPTBigCodeForCausalLM", "GPTJForCausalLM",
        "GPTNeoXForCausalLM", "Gemma2ForCausalLM", "Gemma3ForCausalLM", "Gemma3ForConditionalGeneration",
        "Gemma3nForCausalLM", "Gemma3nForConditionalGeneration", "Gemma4ForCausalLM",
        "Gemma4ForConditionalGeneration", "Gemma4UnifiedForConditionalGeneration", "GemmaForCausalLM",
        "Glm4ForCausalLM", "Glm4MoeForCausalLM", "Glm4MoeLiteForCausalLM", "Glm4vForConditionalGeneration",
        "Glm4vMoeForConditionalGeneration", "GlmForCausalLM", "GlmMoeDsaForCausalLM",
        "GlmOcrForConditionalGeneration", "GptOssForCausalLM", "Granite4VisionForConditionalGeneration",
        "GraniteForCausalLM", "GraniteMoeForCausalLM", "GraniteMoeHybridForCausalLM", "GraniteMoeSWAForCausalLM",
        "GraniteMoeSharedForCausalLM", "GraniteSWAForCausalLM", "H2OVLChatModel", "HCXVisionV2ForCausalLM",
        "HYV3ForCausalLM", "HYV4ForCausalLM", "HrmTextForCausalLM", "HunYuanDenseV1ForCausalLM",
        "HunYuanMoEV1ForCausalLM", "HunYuanVLForConditionalGeneration", "HyperCLOVAXForCausalLM",
        "IQuestCoderForCausalLM", "IQuestLoopCoderForCausalLM", "Idefics3ForConditionalGeneration",
        "InternLM2ForCausalLM", "InternLM3ForCausalLM", "InternS1ForConditionalGeneration",
        "InternS1ProForConditionalGeneration", "InternS2MobiusForConditionalGeneration",
        "InternS2PreviewForConditionalGeneration", "InternVLChatModel", "InternVLForConditionalGeneration",
        "IsaacForConditionalGeneration", "Jais2ForCausalLM", "JambaForCausalLM", "K2HorizonForCausalLM",
        "KananaVForConditionalGeneration", "KimiK25ForConditionalGeneration", "KimiK3ForConditionalGeneration",
        "KimiLinearForCausalLM", "KimiVLForConditionalGeneration", "Kolibri1ForCausalLM", "Lfm2ForCausalLM",
        "Lfm2MoeForCausalLM", "Lfm2VlForConditionalGeneration", "LightOnOCRForConditionalGeneration",
        "Llama4ForConditionalGeneration", "LlamaForCausalLM", "LlavaForConditionalGeneration",
        "LlavaNextForConditionalGeneration", "LlavaOnevisionForConditionalGeneration", "LongcatFlashForCausalLM",
        "Mamba2ForCausalLM", "MambaForCausalLM", "MellumForCausalLM", "MiMoForCausalLM", "MiMoV2FlashForCausalLM",
        "MiMoV2ForCausalLM", "MiMoV2OmniForCausalLM", "MiniCPM3ForCausalLM", "MiniCPMForCausalLM", "MiniCPMO",
        "MiniCPMV", "MiniMaxM2ForCausalLM", "MiniMaxM3SparseForCausalLM", "MiniMaxM3SparseForConditionalGeneration",
        "MiniMaxVL01ForConditionalGeneration", "Mistral3ForConditionalGeneration", "MistralForCausalLM",
        "MistralLarge3ForCausalLM", "MixtralForCausalLM", "Molmo2ForConditionalGeneration", "MolmoForCausalLM",
        "Moondream3ForCausalLM", "MuseGlimmerForCausalLM", "MuseGlimmerForConditionalGeneration",
        "MuseSparkForConditionalGeneration", "NVLM_D_Model", "NanbeigeForCausalLM", "NemotronForCausalLM",
        "NemotronHForCausalLM", "OPTForCausalLM", "Olmo2ForCausalLM", "Olmo3ForCausalLM", "OlmoForCausalLM",
        "OlmoHybridForCausalLM", "OlmoeForCausalLM", "OpenCUAForConditionalGeneration",
        "OpenPanguVLForConditionalGeneration", "OrionForCausalLM", "Ovis", "Ovis2_5", "Ovis2_6ForCausalLM",
        "Ovis2_6_MoeForCausalLM", "PaddleOCRVLForConditionalGeneration", "PaliGemmaForConditionalGeneration",
        "PanguEmbeddedForCausalLM", "PanguProMoEV2ForCausalLM", "PanguUltraMoEForCausalLM", "Param2MoEForCausalLM",
        "Phi3ForCausalLM", "Phi3VForCausalLM", "Phi4MMForCausalLM", "PhiForCausalLM", "PhiMoEForCausalLM",
        "PixtralForConditionalGeneration", "Plamo3ForCausalLM", "Qwen2AudioForConditionalGeneration",
        "Qwen2ForCausalLM", "Qwen2MoeForCausalLM", "Qwen2VLForConditionalGeneration",
        "Qwen2_5OmniThinkerForConditionalGeneration", "Qwen2_5_VLForConditionalGeneration", "Qwen3ForCausalLM",
        "Qwen3MoeForCausalLM", "Qwen3NextForCausalLM", "Qwen3OmniMoeThinkerForConditionalGeneration",
        "Qwen3VLForConditionalGeneration", "Qwen3VLMoeForConditionalGeneration", "Qwen3_5ForConditionalGeneration",
        "Qwen3_5MoeForConditionalGeneration", "RForConditionalGeneration", "Rnj1ForCausalLM", "SarvamMLAForCausalLM",
        "SarvamMoEForCausalLM", "SeedOssForCausalLM", "SkyworkR1VChatModel", "SmolLM3ForCausalLM",
        "SmolVLMForConditionalGeneration", "SolarForCausalLM", "StableLmForCausalLM", "Starcoder2ForCausalLM",
        "Step1ForCausalLM", "Step3VLForConditionalGeneration", "Step3p5ForCausalLM",
        "Step3p7ForConditionalGeneration", "Step5ForConditionalGeneration", "StepVLForConditionalGeneration",
        "TeleChat2ForCausalLM", "TeleChat3ForCausalLM", "TeleFLMForCausalLM", "UltravoxModel",
        "VaultGemmaForCausalLM", "Zamba2ForCausalLM",
    )

    /** 벤더 포크에서만 도는 아키텍처와 그 포크. */
    val FORKS: Map<String, String> = mapOf(
        "Raon1MiniForCausalLM" to "KRAFTON vLLM 포크 (ref raon-1-mini-preview)",
    )
}
