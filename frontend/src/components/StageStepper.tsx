import { CheckCircle2, Circle, Sparkles } from "lucide-react";
import type { InterviewStage } from "../types/interview";

const STAGES: { key: InterviewStage; label: string; subtitle: string }[] = [
  { key: "INTRO", label: "1. Introduction", subtitle: "Warm-up & background" },
  { key: "TECHNICAL", label: "2. Technical", subtitle: "Deep-dive & architecture" },
  { key: "BEHAVIORAL", label: "3. Behavioral", subtitle: "STAR method & teamwork" },
  { key: "COMPLETED", label: "4. Completed", subtitle: "Wrap-up & feedback" },
];

interface StageStepperProps {
  currentStage: InterviewStage;
  turnCount: number;
  targetRole: string;
  onCompleteSession: () => void;
  isCompleting: boolean;
  onResetSession: () => void;
}

export function StageStepper({
  currentStage,
  turnCount,
  targetRole,
  onCompleteSession,
  isCompleting,
  onResetSession,
}: StageStepperProps) {
  const activeIdx = STAGES.findIndex((s) => s.key === currentStage);

  return (
    <header className="border-b border-slate-200 bg-white/90 backdrop-blur-md px-6 py-4 shadow-xs">
      <div className="max-w-5xl mx-auto flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-xl bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-600">
            <Sparkles className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-base font-semibold text-slate-900">
                AI Technical Interviewer
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-medium">
                {targetRole}
              </span>
            </div>
            <p className="text-xs text-slate-500">
              Turn #{turnCount} • Real-time SSE Streaming
            </p>
          </div>
        </div>

        <nav className="flex items-center gap-2 overflow-x-auto py-1">
          {STAGES.map((stage, idx) => {
            const isDone = idx < activeIdx;
            const isCurrent = idx === activeIdx;

            return (
              <div
                key={stage.key}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs transition-colors ${
                  isCurrent
                    ? "bg-indigo-50 border-indigo-500 text-indigo-700 font-semibold"
                    : isDone
                    ? "bg-emerald-50 border-emerald-200 text-emerald-700"
                    : "bg-slate-50 border-slate-200 text-slate-400"
                }`}
              >
                {isDone ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 shrink-0" />
                ) : (
                  <Circle
                    className={`h-3.5 w-3.5 shrink-0 ${
                      isCurrent ? "text-indigo-600 fill-indigo-600/20" : "text-slate-300"
                    }`}
                  />
                )}
                <div className="whitespace-nowrap">
                  <div>{stage.label}</div>
                </div>
              </div>
            );
          })}
        </nav>

        <div className="flex items-center gap-2 self-start md:self-auto">
          {currentStage !== "COMPLETED" && turnCount > 0 && (
            <button
              type="button"
              onClick={onCompleteSession}
              disabled={isCompleting}
              className="text-xs font-medium text-indigo-700 hover:text-indigo-800 bg-indigo-50 hover:bg-indigo-100 disabled:opacity-50 px-3 py-1.5 rounded-lg border border-indigo-200 transition-colors cursor-pointer"
            >
              {isCompleting ? "Ending..." : "End & Evaluate"}
            </button>
          )}
          <button
            type="button"
            onClick={onResetSession}
            className="text-xs text-slate-600 hover:text-slate-900 bg-white hover:bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-200 hover:border-slate-300 transition-colors cursor-pointer"
          >
            New Session
          </button>
        </div>
      </div>
    </header>
  );
}
