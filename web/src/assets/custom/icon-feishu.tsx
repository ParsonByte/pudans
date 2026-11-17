import type { SVGProps } from "react";

type FeishuIconProps = {
  size?: number;
  className?: string;
} & SVGProps<SVGSVGElement>;

export function FeishuIcon({ size = 20, className, ...rest }: FeishuIconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 20.0045 20"
      fill="none"
      className={className}
      aria-hidden="true"
      {...rest}
    >
      <path
        fillRule="evenodd"
        fill="#00DAB8"
        d="M10.4458,10.7557c0,0 -2.39001,-5.56996 -7.76001,-8.96996h9.55001c0,0 2.69,2.48 2.69,5.88z"
      />
      <path
        fillRule="evenodd"
        fill="#013DA0"
        d="M20.006,7.05464c0,0 -0.9,0.61 -2.09,3.70996c-1.34,3.47 -5.08,5.57 -5.08,5.57c0,0 -4.65003,-2.9 -6.58003,-4.05c1.65,0.5 3.43,-1.35 4.49003,-2.44996c0.9,-0.93 1.79,-2.17 3.88,-3.09c2.09,-0.93 5.38,0.31 5.38,0.31zM6.25597,12.2846c-0.2,-0.06 -0.39,-0.15 -0.58,-0.28c-0.5,-0.35 -0.17,-0.17 0.58,0.28z"
      />
      <path
        fillRule="evenodd"
        fill="#1A72FF"
        d="M0 6.42722ZM0 15.7072C0 15.7072 3.29 19.7572 11.04 17.5672C14.22 16.6672 16.84 12.5872 17.01 12.3172C16.8 12.6472 12.77 18.4672 0 6.42722L0 15.7072Z"
      />
    </svg>
  );
}
