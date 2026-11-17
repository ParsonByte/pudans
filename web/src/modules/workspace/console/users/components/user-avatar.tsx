import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";

type UserAvatarProps = {
  name?: string;
  displayName?: string;
  avatar?: string;
  className?: string;
};

export function UserAvatar({ name, displayName, avatar, className }: UserAvatarProps) {
  const fallback = (displayName || name || "?").trim().slice(0, 1).toUpperCase();

  return (
    <Avatar className={cn("size-9", className)}>
      <AvatarImage src={avatar || undefined} alt={displayName || name || "用户头像"} />
      <AvatarFallback>{fallback}</AvatarFallback>
    </Avatar>
  );
}
