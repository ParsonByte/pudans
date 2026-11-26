package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record MiniMaxSpeechData(String audio, Integer status) {}
