import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  Briefcase,
  CheckCircle2,
  FileText,
  Loader2,
  Play,
  Sparkles,
  UploadCloud,
} from "lucide-react";
import { createSession, uploadResume } from "../api/interviewApi";
import type { ResumeResponse, SessionResponse } from "../types/interview";

interface SetupScreenProps {
  onSessionCreated: (
    session: SessionResponse,
    resume: ResumeResponse
  ) => void;
}

const ROLE_PRESETS = [
  "Senior Java Backend Engineer",
  "Full-Stack Software Engineer",
  "Distributed Systems & Platform Engineer",
  "AI / Backend Infrastructure Engineer",
];

export function SetupScreen({ onSessionCreated }: SetupScreenProps) {
  const [targetRole, setTargetRole] = useState<string>(
    "Senior Java Backend Engineer"
  );
  const [uploadedResume, setUploadedResume] = useState<ResumeResponse | null>(
    null
  );

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadResume(file),
    onSuccess: (data) => {
      setUploadedResume(data);
    },
  });

  const createSessionMutation = useMutation({
    mutationFn: () => {
      if (!uploadedResume) {
        throw new Error("Please upload your PDF resume before starting.");
      }
      return createSession({
        targetRole: targetRole.trim() || "Software Engineer",
        resumeId: uploadedResume.id,
      });
    },
    onSuccess: (session) => {
      if (uploadedResume) {
        onSessionCreated(session, uploadedResume);
      }
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      uploadMutation.mutate(file);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-gradient-to-b from-slate-50 via-indigo-50/30 to-slate-100">
      <div className="w-full max-w-2xl rounded-2xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-200/60">
        <div className="flex items-center gap-3 mb-6">
          <div className="h-11 w-11 rounded-xl bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-600">
            <Sparkles className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-900">
              AI Interview Agent
            </h1>
            <p className="text-sm text-slate-500">
              Upload your PDF resume for personalized technical & behavioral
              questions
            </p>
          </div>
        </div>

        {/* Step 1: Target Role */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-slate-700 mb-2 flex items-center gap-2">
            <Briefcase className="h-4 w-4 text-indigo-600" />
            Target Role
          </label>
          <input
            type="text"
            value={targetRole}
            onChange={(e) => setTargetRole(e.target.value)}
            placeholder="e.g., Senior Java Backend Engineer"
            className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none"
          />
          <div className="flex flex-wrap gap-2 mt-2.5">
            {ROLE_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                onClick={() => setTargetRole(preset)}
                className={`text-xs px-3 py-1 rounded-full border transition-colors cursor-pointer ${
                  targetRole === preset
                    ? "bg-indigo-50 border-indigo-500 text-indigo-700 font-medium"
                    : "bg-slate-50 border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-100"
                }`}
              >
                {preset}
              </button>
            ))}
          </div>
        </div>

        {/* Step 2: PDF Resume Upload */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-slate-700 mb-2 flex items-center gap-2">
            <FileText className="h-4 w-4 text-indigo-600" />
            Candidate Resume (PDF — Required)
          </label>

          <label className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 hover:border-indigo-500 bg-slate-50/70 hover:bg-indigo-50/30 p-6 cursor-pointer transition-colors">
            <input
              type="file"
              accept="application/pdf,.pdf"
              onChange={handleFileChange}
              className="hidden"
              disabled={uploadMutation.isPending}
            />
            {uploadMutation.isPending ? (
              <div className="flex items-center gap-3 text-indigo-600 py-2">
                <Loader2 className="h-5 w-5 animate-spin" />
                <span className="text-sm font-medium">
                  Extracting PDF text & parsing skills with Ollama...
                </span>
              </div>
            ) : uploadedResume ? (
              <div className="w-full">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2 text-emerald-700 font-medium text-sm">
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    <span>{uploadedResume.fileName} parsed!</span>
                  </div>
                  <span className="text-xs text-slate-500 underline">
                    Click to replace PDF
                  </span>
                </div>
                {uploadedResume.experienceSummary && (
                  <p className="text-xs text-slate-700 mb-3 bg-white p-2.5 rounded-lg border border-slate-200">
                    {uploadedResume.experienceSummary}
                  </p>
                )}
                <div className="flex flex-wrap gap-1.5">
                  {uploadedResume.parsedSkills.map((skill) => (
                    <span
                      key={skill}
                      className="text-xs px-2.5 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium"
                    >
                      {skill}
                    </span>
                  ))}
                </div>
              </div>
            ) : (
              <div className="text-center">
                <UploadCloud className="h-8 w-8 text-indigo-500 mx-auto mb-2" />
                <p className="text-sm text-slate-700 font-medium">
                  Click to upload your PDF resume
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  Parsed via Apache PDFBox + FastAPI Ollama JSON mode
                </p>
              </div>
            )}
          </label>

          {uploadMutation.isError && (
            <p className="mt-2 text-xs text-rose-600">
              {uploadMutation.error.message}
            </p>
          )}
        </div>

        {/* Step 3: Start Session */}
        <button
          type="button"
          onClick={() => createSessionMutation.mutate()}
          disabled={
            !uploadedResume ||
            uploadMutation.isPending ||
            createSessionMutation.isPending
          }
          className="w-full flex items-center justify-center gap-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed px-5 py-3 text-sm font-semibold text-white shadow-md shadow-indigo-600/20 transition-colors cursor-pointer"
        >
          {createSessionMutation.isPending ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              Creating Interview Session...
            </>
          ) : !uploadedResume ? (
            <>
              <UploadCloud className="h-4 w-4" />
              Upload PDF Resume Above to Start
            </>
          ) : (
            <>
              <Play className="h-4 w-4 fill-current" />
              Start Mock Interview
            </>
          )}
        </button>

        {createSessionMutation.isError && (
          <p className="mt-3 text-xs text-rose-600 text-center">
            {createSessionMutation.error.message}
          </p>
        )}
      </div>
    </div>
  );
}
