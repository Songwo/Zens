type AvatarMarkProps = {
  value?: string;
  fallback: string;
  className?: string;
  imageClassName?: string;
};

export function AvatarMark({ value, fallback, className = "", imageClassName = "" }: AvatarMarkProps) {
  const text = shortAvatarText(value || fallback);
  return (
    <span className={`avatar-mark overflow-hidden ${className}`}>
      {isImageAvatar(value) ? (
        <img className={`h-full w-full object-cover ${imageClassName}`} src={value} alt="" loading="lazy" />
      ) : (
        <span className="block max-w-full truncate px-1">{text}</span>
      )}
    </span>
  );
}

export function isImageAvatar(value?: string) {
  return Boolean(value && /^(https?:|\/|data:image\/)/i.test(value));
}

function shortAvatarText(value: string) {
  const clean = value.trim();
  if (!clean) return "U";
  if (/^(https?:|\/|data:image\/)/i.test(clean)) return "U";
  return Array.from(clean).slice(0, 2).join("").toUpperCase();
}
