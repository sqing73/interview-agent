import {
  Award,
  CheckCircle2,
  Loader2,
  Sparkles,
  TrendingUp,
} from "lucide-react";
import type { EvaluationReport, HiringRecommendation } from "../types/interview";

interface EvaluationScorecardProps {
  report: EvaluationReport | null | undefined;
  targetRole: string;
}

const RECOMMENDATION_STYLES: Record<
  HiringRecommendation,
  { label: string; className: string }
> = {
  STRONG_HIRE: {
    label: "Strong Hire",
    className: "bg-emerald-50 text-emerald-700 border-emerald-200",
  },
  HIRE: {
    label: "Hire",
    className: "bg-indigo-50 text-indigo-700 border-indigo-200",
  },
  LEAN_HIRE: {
    label: "Lean Hire",
    className: "bg-amber-50 text-amber-700 border-amber-200",
  },
  NO_HIRE: {
    label: "Needs Practice",
    className: "bg-rose-50 text-rose-700 border-rose-200",
  },
};

function ScoreBar({ label, score }: { label: string; score: number }) {
  const color =
    score >= 80
      ? "bg-emerald-500"
      : score >= 65
      ? "bg-indigo-500"
      : "bg-amber-500";

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-medium text-slate-600">{label}</span>
        <span className="text-sm font-bold text-slate-900">{score}/100</span>
      </div>
      <div className="h-2 w-full rounded-full bg-slate-200 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ${color}`}
          style={{ width: `${Math.min(100, Math.max(0, score))}%` }}
        />
      </div>
    </div>
  );
}

export function EvaluationScorecard({
  report,
  targetRole,
}: EvaluationScorecardProps) {
  if (!report) {
    return (
      <div className="rounded-2xl border border-indigo-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-600">
            <Loader2 className="h-5 w-5 animate-spin" />
          </div>
          <div>
            <h3 className="text-sm font-semibold text-slate-900">
              Generating Post-Interview Evaluation Report...
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Analyzing your {targetRole} transcript in the background for
              technical depth, communication, and problem solving.
            </p>
          </div>
        </div>
      </div>
    );
  }

  const badge =
    RECOMMENDATION_STYLES[report.recommendation] ??
    RECOMMENDATION_STYLES.LEAN_HIRE;

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-slate-100 pb-4">
        <div className="flex items-center gap-3">
          <div className="h-11 w-11 rounded-xl bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-600">
            <Award className="h-6 w-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-slate-900">
                Post-Interview Evaluation Scorecard
              </h2>
              <span
                className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${badge.className}`}
              >
                {badge.label}
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Automated Hiring Committee Rubric • {targetRole}
            </p>
          </div>
        </div>

        <div className="flex items-baseline gap-1.5 bg-indigo-50/70 border border-indigo-100 rounded-xl px-4 py-2 self-start sm:self-auto">
          <span className="text-xs font-medium text-indigo-600">Overall</span>
          <span className="text-2xl font-bold text-indigo-700">
            {report.overall_score}
          </span>
          <span className="text-xs text-indigo-400">/100</span>
        </div>
      </div>

      {report.summary && (
        <div className="rounded-xl bg-slate-50 border border-slate-200/80 p-4">
          <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-700 mb-1">
            <Sparkles className="h-3.5 w-3.5 text-indigo-600" />
            Executive Summary
          </div>
          <p className="text-sm text-slate-700 leading-relaxed">
            {report.summary}
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <ScoreBar label="Technical Depth" score={report.technical_score} />
        <ScoreBar label="Communication" score={report.communication_score} />
        <ScoreBar
          label="Problem Solving"
          score={report.problem_solving_score}
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="rounded-xl border border-emerald-200/80 bg-emerald-50/40 p-4">
          <div className="flex items-center gap-2 text-xs font-semibold text-emerald-800 mb-2.5">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            Key Strengths Demonstrated
          </div>
          {report.strengths.length > 0 ? (
            <ul className="space-y-1.5 text-xs text-slate-700">
              {report.strengths.map((item, idx) => (
                <li key={idx} className="flex items-start gap-2">
                  <span className="text-emerald-600 font-bold">•</span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-xs text-slate-500">No specific strengths listed.</p>
          )}
        </div>

        <div className="rounded-xl border border-amber-200/80 bg-amber-50/40 p-4">
          <div className="flex items-center gap-2 text-xs font-semibold text-amber-800 mb-2.5">
            <TrendingUp className="h-4 w-4 text-amber-600" />
            Areas for Improvement
          </div>
          {report.areas_for_improvement.length > 0 ? (
            <ul className="space-y-1.5 text-xs text-slate-700">
              {report.areas_for_improvement.map((item, idx) => (
                <li key={idx} className="flex items-start gap-2">
                  <span className="text-amber-600 font-bold">•</span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-xs text-slate-500">
              No specific improvement areas listed.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
