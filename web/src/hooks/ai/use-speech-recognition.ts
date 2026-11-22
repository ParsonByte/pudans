"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

type SpeechAlternative = {
    transcript: string;
    confidence: number;
};

type SpeechResult = {
    isFinal: boolean;
    length: number;
    [index: number]: SpeechAlternative;
};

type SpeechResultList = {
    length: number;
    [index: number]: SpeechResult;
};

type SpeechResultEvent = {
    resultIndex: number;
    results: SpeechResultList;
};

type SpeechErrorEvent = {
    error: string;
    message: string;
};

type SpeechRecognitionLike = {
    lang: string;
    continuous: boolean;
    interimResults: boolean;
    start: () => void;
    stop: () => void;
    abort: () => void;
    onresult: ((event: SpeechResultEvent) => void) | null;
    onerror: ((event: SpeechErrorEvent) => void) | null;
    onend: (() => void) | null;
};

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

type SpeechWindow = Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

export type SpeechRecognitionOptions = {
    lang?: string;
    continuous?: boolean;
    interimResults?: boolean;
};

function subscribe() {
    return () => {};
}

function getRecognitionConstructor() {
    const speechWindow = window as SpeechWindow;
    return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition;
}

function getServerSnapshot() {
    return false;
}

export function useSpeechRecognition({
    lang = "zh-CN",
    continuous = false,
    interimResults = true,
}: SpeechRecognitionOptions = {}) {
    const [listening, setListening] = useState(false);
    const [transcript, setTranscript] = useState("");
    const [interimTranscript, setInterimTranscript] = useState("");
    const [error, setError] = useState<string | null>(null);
    const recognitionRef = useRef<SpeechRecognitionLike | null>(null);

    const supported = useSyncExternalStore(subscribe, () => Boolean(getRecognitionConstructor()), getServerSnapshot);

    const start = useCallback(() => {
        const Recognition = getRecognitionConstructor();
        if (!Recognition) {
            setError("当前浏览器不支持语音识别。");
            return;
        }
        if (listening) {
            return;
        }

        const recognition = recognitionRef.current ?? new Recognition();
        recognitionRef.current = recognition;
        recognition.lang = lang;
        recognition.continuous = continuous;
        recognition.interimResults = interimResults;

        recognition.onresult = (event) => {
            let finalText = "";
            let interim = "";

            for (let index = event.resultIndex; index < event.results.length; index += 1) {
                const result = event.results[index];
                const alternative = result?.[0];
                if (!alternative) {
                    continue;
                }

                if (result.isFinal) {
                    finalText += alternative.transcript;
                } else {
                    interim += alternative.transcript;
                }
            }

            if (finalText) {
                setTranscript((previous) => `${previous}${finalText}`);
            }
            setInterimTranscript(interim);
        };
        recognition.onerror = (event) => {
            setError(event.error);
            setListening(false);
        };
        recognition.onend = () => {
            setListening(false);
            setInterimTranscript("");
        };

        setError(null);
        try {
            recognition.start();
            setListening(true);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : String(cause));
        }
    }, [listening, lang, continuous, interimResults]);

    const stop = useCallback(() => {
        recognitionRef.current?.stop();
    }, []);

    const reset = useCallback(() => {
        setTranscript("");
        setInterimTranscript("");
        setError(null);
    }, []);

    useEffect(() => {
        return () => recognitionRef.current?.abort();
    }, []);

    return {
        supported,
        listening,
        transcript,
        interimTranscript,
        error,
        start,
        stop,
        reset,
    };
}
