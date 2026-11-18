export type InterviewStage = "INTRO" | "TECHNICAL" | "BEHAVIORAL" | "COMPLETED";

export type MessageRole = "SYSTEM" | "USER" | "ASSISTANT";

export interface ResumeResponse {
  id: string;
  userId: string;
  fileName: string;
  extractedText: string;
  parsedSkills: string[];
  experienceSummary: string;
  createdAt: string;
}

export interface CreateSessionRequest {
  userId?: string;
  resumeId: string;
  targetRole: string;
}

export interface SessionResponse {
  id: string;
  userId: string;
  resumeId: string;
  targetRole: string;
  stage: InterviewStage;
  turnCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface MessageResponse {
  id: string;
  sessionId: string;
  role: MessageRole;
  stage: InterviewStage;
  content: string;
  createdAt: string;
}

export interface AiStreamChunk {
  token?: string;
  done?: boolean;
  error?: string;
}
