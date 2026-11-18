import type {
  AiStreamChunk,
  CreateSessionRequest,
  MessageResponse,
  ResumeResponse,
  SessionResponse,
} from "../types/interview";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export async function uploadResume(file: File): Promise<ResumeResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE_URL}/api/resumes`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `Failed to upload resume (HTTP ${response.status})`);
  }

  return response.json() as Promise<ResumeResponse>;
}

export async function createSession(
  request: CreateSessionRequest
): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `Failed to create session (HTTP ${response.status})`);
  }

  return response.json() as Promise<SessionResponse>;
}

export async function getSession(sessionId: string): Promise<SessionResponse> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch session (HTTP ${response.status})`);
  }
  return response.json() as Promise<SessionResponse>;
}

export async function getMessages(sessionId: string): Promise<MessageResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/messages`);
  if (!response.ok) {
    throw new Error(`Failed to fetch messages (HTTP ${response.status})`);
  }
  return response.json() as Promise<MessageResponse[]>;
}

/**
 * Streams an interview chat turn over POST using the Fetch ReadableStream API
 * and parses incoming Server-Sent Events (SSE) `data:` frames.
 */
export async function streamCandidateMessage(
  sessionId: string,
  content: string,
  onToken: (token: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/sessions/${sessionId}/messages`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ content }),
    signal,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `Stream request failed (HTTP ${response.status})`);
  }

  if (!response.body) {
    throw new Error("ReadableStream not supported by this browser response.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line.startsWith("data:")) {
        continue;
      }

      const jsonStr = line.slice("data:".length).trim();
      if (!jsonStr) {
        continue;
      }

      const chunk = JSON.parse(jsonStr) as AiStreamChunk;
      if (chunk.error) {
        throw new Error(chunk.error);
      }
      if (chunk.token) {
        onToken(chunk.token);
      }
      if (chunk.done) {
        return;
      }
    }
  }
}
