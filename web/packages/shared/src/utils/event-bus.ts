type Listener<T> = (payload: T) => void;

/** Small typed event bus for cross-component or plugin communication. */
export class EventBus<TEvents extends Record<string, unknown>> {
  private readonly listeners = new Map<keyof TEvents, Set<Listener<never>>>();

  on<K extends keyof TEvents>(event: K, listener: Listener<TEvents[K]>): () => void {
    const listeners = this.listeners.get(event) ?? new Set<Listener<never>>();
    listeners.add(listener as Listener<never>);
    this.listeners.set(event, listeners);
    return () => this.off(event, listener);
  }

  once<K extends keyof TEvents>(event: K, listener: Listener<TEvents[K]>): () => void {
    const unsubscribe = this.on(event, (payload) => {
      unsubscribe();
      listener(payload);
    });
    return unsubscribe;
  }

  off<K extends keyof TEvents>(event: K, listener: Listener<TEvents[K]>): void {
    const listeners = this.listeners.get(event);
    listeners?.delete(listener as Listener<never>);
    if (listeners?.size === 0) {
      this.listeners.delete(event);
    }
  }

  emit<K extends keyof TEvents>(event: K, payload: TEvents[K]): void {
    const listeners = this.listeners.get(event);
    if (!listeners) {
      return;
    }
    for (const listener of [...listeners]) {
      (listener as Listener<TEvents[K]>)(payload);
    }
  }

  clear(event?: keyof TEvents): void {
    if (event === undefined) {
      this.listeners.clear();
      return;
    }
    this.listeners.delete(event);
  }
}

export function createEventBus<TEvents extends Record<string, unknown>>(): EventBus<TEvents> {
  return new EventBus<TEvents>();
}
