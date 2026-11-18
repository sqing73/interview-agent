import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { Bot, Loader2, Send, Sparkles, User } from "lucide-react";
import { useInterviewStream } from "../hooks/useInterviewStream";
import { EvaluationScorecard } from "./EvaluationScorecard";
import { StageStepper } from "./StageStepper";
import type { ResumeResponse, SessionResponse } from "../types/interview";

interface ChatWorkspaceProps {
  initialSession: SessionResponse;
  resume: ResumeResponse | null;
  onResetSession: () => void;
}

export function ChatWorkspace({
  initialSession,
  resume,
  onResetSession,
}: ChatWorkspaceProps) {
  const {
    session,
    messages,
    streamingContent,
    optimisticUserMessage,
    isStreaming,
    streamError,
    sendMessage,
    completeInterview,
    isCompleting,
  } = useInterviewStream(initialSession.id);

  const [input, setInput] = useState<string>("");
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const hasAutoStartedRef = useRef<boolean>(false);

  const activeSession = session ?? initialSession;

  // Automatically kick off the opening greeting when a fresh session starts
  useEffect(() => {
    if (
      !hasAutoStartedRef.current &&
      activeSession.turnCount === 0 &&
      messages.length === 0 &&
      !isStreaming
    ) {
      hasAutoStartedRef.current = true;
      void sendMessage(
        "Hello! I am ready to begin the interview. Please introduce yourself and ask your first question."
      );
    }
  }, [activeSession.turnCount, isStreaming, messages.length, sendMessage]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [
    messages.length,
    streamingContent,
    optimisticUserMessage,
    activeSession.stage,
    activeSession.evaluationReport,
  ]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isStreaming) {
      return;
    }
    const text = input;
    setInput("");
    void sendMessage(text);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const visibleMessages = messages.filter((m) => m.role !== "SYSTEM");

  return (
    <div className="flex flex-col h-screen bg-slate-50 text-slate-900">
      <StageStepper
        currentStage={activeSession.stage}
        turnCount={activeSession.turnCount}
        targetRole={activeSession.targetRole}
        onCompleteSession={completeInterview}
        isCompleting={isCompleting}
        onResetSession={onResetSession}
      />

      {resume && resume.parsedSkills.length > 0 && (
        <div className="bg-indigo-50/50 border-b border-slate-200 px-6 py-2">
          <div className="max-w-4xl mx-auto flex items-center gap-2 overflow-x-auto text-xs">
            <span className="text-slate-600 shrink-0 font-medium">
              Resume Skills:
            </span>
            {resume.parsedSkills.map((skill) => (
              <span
                key={skill}
                className="px-2 py-0.5 rounded bg-white text-indigo-700 border border-indigo-200 font-medium whitespace-nowrap"
              >
                {skill}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Message List */}
      <main className="flex-1 overflow-y-auto px-4 py-6">
        <div className="max-w-4xl mx-auto space-y-6">
          {visibleMessages.map((msg) => {
            const isUser = msg.role === "USER";
            return (
              <div
                key={msg.id}
                className={`flex gap-3.5 ${
                  isUser ? "justify-end" : "justify-start"
                }`}
              >
                {!isUser && (
                  <div className="h-8 w-8 rounded-lg bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-600 shrink-0 mt-1">
                    <Bot className="h-4 w-4" />
                  </div>
                )}

                <div
                  className={`max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-xs ${
                    isUser
                      ? "bg-indigo-600 text-white rounded-br-sm"
                      : "bg-white border border-slate-200 text-slate-800 rounded-bl-sm"
                  }`}
                >
                  <div
                    className={`flex items-center gap-2 mb-1 text-[11px] ${
                      isUser ? "text-indigo-100" : "text-slate-400"
                    }`}
                  >
                    <span className="font-semibold">
                      {isUser ? "Candidate" : "AI Interviewer"}
                    </span>
                    <span>•</span>
                    <span>{msg.stage}</span>
                  </div>
                  <div className="prose prose-sm max-w-none">
                    <ReactMarkdown>{msg.content}</ReactMarkdown>
                  </div>
                </div>

                {isUser && (
                  <div className="h-8 w-8 rounded-lg bg-slate-200 border border-slate-300 flex items-center justify-center text-slate-700 shrink-0 mt-1">
                    <User className="h-4 w-4" />
                  </div>
                )}
              </div>
            );
          })}

          {/* Optimistic candidate message while streaming */}
          {optimisticUserMessage && (
            <div className="flex gap-3.5 justify-end">
              <div className="max-w-[80%] rounded-2xl rounded-br-sm bg-indigo-600 text-white px-4 py-3 text-sm leading-relaxed shadow-xs">
                <div className="text-[11px] text-indigo-100 font-semibold mb-1">
                  Candidate
                </div>
                <div>{optimisticUserMessage}</div>
              </div>
              <div className="h-8 w-8 rounded-lg bg-slate-200 border border-slate-300 flex items-center justify-center text-slate-700 shrink-0 mt-1">
                <User className="h-4 w-4" />
              </div>
            </div>
          )}

          {/* Live streaming AI bubble */}
          {isStreaming && (
            <div className="flex gap-3.5 justify-start">
              <div className="h-8 w-8 rounded-lg bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-600 shrink-0 mt-1">
                <Sparkles className="h-4 w-4 animate-pulse" />
              </div>
              <div className="max-w-[80%] rounded-2xl rounded-bl-sm bg-white border border-indigo-300 text-slate-800 px-4 py-3 text-sm leading-relaxed shadow-xs">
                <div className="flex items-center gap-2 mb-1 text-[11px] text-indigo-600 font-semibold">
                  <span>AI Interviewer</span>
                  <span>•</span>
                  <span>Streaming...</span>
                </div>
                {streamingContent ? (
                  <div className="prose prose-sm max-w-none">
                    <ReactMarkdown>{streamingContent}</ReactMarkdown>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-slate-500 text-xs py-1">
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-indigo-600" />
                    <span>Thinking...</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {streamError && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs text-rose-700">
              Stream error: {streamError}
            </div>
          )}

          {activeSession.stage === "COMPLETED" && !isStreaming && (
            <EvaluationScorecard
              report={activeSession.evaluationReport}
              targetRole={activeSession.targetRole}
            />
          )}

          <div ref={bottomRef} />
        </div>
      </main>

      {/* Composer */}
      <footer className="border-t border-slate-200 bg-white px-4 py-4">
        <form
          onSubmit={handleSubmit}
          className="max-w-4xl mx-auto flex items-end gap-3"
        >
          <textarea
            rows={2}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isStreaming || activeSession.stage === "COMPLETED"}
            placeholder={
              activeSession.stage === "COMPLETED"
                ? "Interview completed! Click 'New Session' at the top right to start another."
                : "Type your answer... (Press Enter to send, Shift+Enter for new line)"
            }
            className="flex-1 resize-none rounded-xl border border-slate-300 bg-slate-50 px-4 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:bg-white focus:ring-2 focus:ring-indigo-500/20 focus:outline-none disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={
              isStreaming ||
              !input.trim() ||
              activeSession.stage === "COMPLETED"
            }
            className="flex items-center gap-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 px-5 py-3 text-sm font-semibold text-white shadow-sm shadow-indigo-600/20 transition-colors cursor-pointer"
          >
            {isStreaming ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Send className="h-4 w-4" />
            )}
            Send
          </button>
        </form>
      </footer>
    </div>
  );
}
