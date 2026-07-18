import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";

const variantClass: Record<Variant, string> = {
  primary:
    "bg-forest-700 text-white hover:bg-forest-900 shadow-sm disabled:bg-forest-100 disabled:text-muted",
  secondary:
    "border border-border bg-surface text-ink hover:bg-forest-50 disabled:text-muted",
  ghost: "bg-transparent text-forest-900 hover:bg-forest-50 disabled:text-muted",
  danger: "bg-danger text-white hover:opacity-90 disabled:opacity-50",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

export function Button({
  variant = "primary",
  className = "",
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`inline-flex h-10 items-center justify-center gap-2 rounded-control px-4 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info disabled:cursor-not-allowed ${variantClass[variant]} ${className}`}
      {...props}
    />
  );
}
