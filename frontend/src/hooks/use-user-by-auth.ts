import { useQuery } from "@tanstack/react-query";
import { usersApi } from "@/lib/api/users";
import type { UserResponseDto } from "@/types/api";

/**
 * Look up a User Service profile by auth user id, with a 60 s cache so the
 * same id rendered multiple times on a page only triggers one fetch.
 *
 * Returns the raw UserResponseDto when present, plus a few convenience
 * fields the calling component usually wants.
 */
export function useUserByAuth(authUserId?: string | null) {
  const q = useQuery({
    queryKey: ["user-by-auth", authUserId],
    queryFn: () => usersApi.byAuthId(authUserId!),
    enabled: !!authUserId,
    staleTime: 60_000,
    retry: (failureCount, err) => {
      const status = (err as { response?: { status?: number } })?.response?.status;
      return status !== 404 && failureCount < 2;
    },
  });

  const user = q.data as UserResponseDto | undefined;
  const fullName = user
    ? `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim()
    : null;

  return {
    user,
    fullName: fullName || null,
    isLoading: q.isLoading,
    isError: q.isError,
  };
}
