import type { CSSProperties } from "react";

import { Plug } from "lucide-react";

import type { AiProviderItem } from "../lib/api";
import { type ProviderPreset, presetIcon, presetOfProvider } from "../lib/presets";

type ProviderBrandIconProps = {
  preset?: ProviderPreset;
  provider?: AiProviderItem;
  className?: string;
};

export function ProviderBrandIcon({
  preset,
  provider,
  className = "size-5",
}: ProviderBrandIconProps) {
  const selected = preset ?? (provider ? presetOfProvider(provider) : undefined);
  const source = selected ? presetIcon(selected) : undefined;
  if (!source) {
    return <Plug className={className} />;
  }
  if (selected?.id === "openai") {
    const style: CSSProperties = {
      backgroundColor: "currentColor",
      maskImage: `url("${source}")`,
      WebkitMaskImage: `url("${source}")`,
      maskSize: "contain",
      WebkitMaskSize: "contain",
      maskRepeat: "no-repeat",
      WebkitMaskRepeat: "no-repeat",
      maskPosition: "center",
      WebkitMaskPosition: "center",
    };
    return (
      <span className={`inline-block shrink-0 ${className}`} style={style} aria-hidden="true" />
    );
  }
  return <img src={source} alt="" className={className} aria-hidden="true" />;
}
