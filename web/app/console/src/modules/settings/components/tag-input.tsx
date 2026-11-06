import { useState, type KeyboardEvent } from "react";
import { Icon } from "@pudans/components";
import styles from "./tag-input.module.css";

export interface TagInputProps {
  id?: string;
  values: string[];
  placeholder?: string;
  onChange: (values: string[]) => void;
}

export function TagInput({ id, values, placeholder, onChange }: TagInputProps) {
  const [draft, setDraft] = useState("");

  const commit = (): void => {
    const value = draft.trim();
    if (value.length > 0 && !values.includes(value)) {
      onChange([...values, value]);
    }
    setDraft("");
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      commit();
      return;
    }
    if (event.key === "Backspace" && draft.length === 0 && values.length > 0) {
      onChange(values.slice(0, -1));
    }
  };

  return (
    <div className={styles.root}>
      {values.map((value) => (
        <span key={value} className={styles.tag}>
          {value}
          <button
            type="button"
            className={styles.remove}
            aria-label={value}
            onClick={() => onChange(values.filter((item) => item !== value))}
          >
            <Icon name="mdi:close" size={12} />
          </button>
        </span>
      ))}
      <input
        id={id}
        className={styles.input}
        value={draft}
        placeholder={values.length === 0 ? placeholder : undefined}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={onKeyDown}
      />
    </div>
  );
}
