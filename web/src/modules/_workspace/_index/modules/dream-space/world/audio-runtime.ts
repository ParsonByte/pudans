type AudioCue = "footstep" | "memory" | "portal" | "weather";

type DreamAudioRuntime = {
    context: AudioContext | null;
    enabled: boolean;
    master: GainNode | null;
};

const runtime: DreamAudioRuntime = {
    context: null,
    enabled: false,
    master: null,
};

const droneOscillators: OscillatorNode[] = [];

function ensureContext(): AudioContext | null {
    if (typeof window === "undefined") {
        return null;
    }

    if (!runtime.context) {
        runtime.context = new AudioContext();
        runtime.master = runtime.context.createGain();
        runtime.master.gain.value = 0;
        runtime.master.connect(runtime.context.destination);
    }

    return runtime.context;
}

function startDrone(context: AudioContext, master: GainNode): void {
    if (droneOscillators.length > 0) {
        return;
    }

    const filter = context.createBiquadFilter();
    filter.type = "lowpass";
    filter.frequency.value = 220;
    filter.connect(master);

    [55, 82.41, 110].forEach((frequency, index) => {
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.type = index === 1 ? "triangle" : "sine";
        oscillator.frequency.value = frequency;
        gain.gain.value = 0.16 / (index + 1);
        oscillator.connect(gain);
        gain.connect(filter);
        oscillator.start();
        droneOscillators.push(oscillator);
    });
}

export function setDreamAudioEnabled(enabled: boolean): void {
    const context = ensureContext();

    if (!context || !runtime.master) {
        return;
    }

    runtime.enabled = enabled;
    startDrone(context, runtime.master);
    runtime.master.gain.cancelScheduledValues(context.currentTime);
    runtime.master.gain.linearRampToValueAtTime(enabled ? 0.045 : 0, context.currentTime + 0.8);

    if (enabled) {
        void context.resume();
    }
}

export function playDreamCue(cue: AudioCue, intensity = 1): void {
    if (!runtime.enabled) {
        return;
    }

    const context = ensureContext();
    const master = runtime.master;

    if (!context || !master) {
        return;
    }

    const oscillator = context.createOscillator();
    const gain = context.createGain();
    const frequencies: Record<AudioCue, number> = {
        footstep: 96,
        memory: 523.25,
        portal: 659.25,
        weather: 196,
    };

    oscillator.type = cue === "footstep" ? "triangle" : "sine";
    oscillator.frequency.value = frequencies[cue];
    gain.gain.setValueAtTime(0.0001, context.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.04 * intensity, context.currentTime + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + (cue === "footstep" ? 0.09 : 0.5));
    oscillator.connect(gain);
    gain.connect(master);
    oscillator.start();
    oscillator.stop(context.currentTime + 0.55);
}
