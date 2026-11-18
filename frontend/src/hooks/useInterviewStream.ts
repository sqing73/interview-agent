import { useCallback, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  completeSession,
  getMessages,
  getSession,
  streamCandidateMessage,
} from "../api/interviewApi";

export function useInterviewStream(sessionId: string) {
  const queryClient = useQueryClient();
  const [streamingContent, setStreamingContent] = useState<string>("");
  const [optimisticUserMessage, setOptimisticUserMessage] = useState<string | null>(
    null
  );
  const [isStreaming, setIsStreaming] = useState<boolean>(false);
  const [streamError, setStreamError] = useState<string | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  const sessionQuery = useQuery({
    queryKey: ["session", sessionId],
    queryFn: () => getSession(sessionId),
    refetchInterval: (query) => {
      const data = query.state.data;
      if (data?.stage === "COMPLETED" && !data.evaluationReport) {
        return 2500;
      }
      return false;
    },
  });

  const completeMutation = useMutation({
    mutationFn: () => completeSession(sessionId),
    onSuccess: (updatedSession) => {
      queryClient.setQueryData(["session", sessionId], updatedSession);
    },
  });

  const messagesQuery = useQuery({
    queryKey: ["messages", sessionId],
    queryFn: () => getMessages(sessionId),
  });

  const sendMessage = useCallback(
    async (userContent: string) => {
      const trimmed = userContent.trim();
      if (!trimmed || isStreaming) {
        return;
      }

      setStreamError(null);
      setOptimisticUserMessage(trimmed);
      setStreamingContent("");
      setIsStreaming(true);

      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      try {
        await streamCandidateMessage(
          sessionId,
          trimmed,
          (token) => {
            setStreamingContent((prev) => prev + token);
          },
          abortController.signal
        );

        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ["messages", sessionId] }),
          queryClient.invalidateQueries({ queryKey: ["session", sessionId] }),
        ]);
      } catch (err) {
        if ((err as Error).name !== "AbortError") {
          setStreamError(
            err instanceof Error ? err.message : "Failed to stream AI response"
          );
        }
      } finally {
        setOptimisticUserMessage(null);
        setStreamingContent("");
        setIsStreaming(false);
        abortControllerRef.current = null;
      }
    },
    [isStreaming, queryClient, sessionId]
  );

  return {
    session: sessionQuery.data,
    messages: messagesQuery.data ?? [],
    isLoadingInitial: sessionQuery.isLoading || messagesQuery.isLoading,
    streamingContent,
    optimisticUserMessage,
    isStreaming,
    streamError,
    sendMessage,
    completeInterview: () => completeMutation.mutate(),
    isCompleting: completeMutation.isPending,
  };
}
