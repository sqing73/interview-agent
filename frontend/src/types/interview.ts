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

export type HiringRecommendation =
  | "STRONG_HIRE"
  | "HIRE"
  | "LEAN_HIRE"
  | "NO_HIRE";

export interface EvaluationReport {
  technical_score: number;
  communication_score: number;
  problem_solving_score: number;
  overall_score: number;
  recommendation: HiringRecommendation;
  strengths: string[];
  areas_for_improvement: string[];
  summary: string;
}

export interface SessionResponse {
  id: string;
  userId: string;
  resumeId: string;
  targetRole: string;
  stage: InterviewStage;
  turnCount: number;
  evaluationReport?: EvaluationReport | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
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
