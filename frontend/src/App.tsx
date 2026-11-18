import { useState } from "react";
import { SetupScreen } from "./components/SetupScreen";
import { ChatWorkspace } from "./components/ChatWorkspace";
import type { ResumeResponse, SessionResponse } from "./types/interview";

export function App() {
  const [activeSession, setActiveSession] = useState<SessionResponse | null>(
    null
  );
  const [activeResume, setActiveResume] = useState<ResumeResponse | null>(null);

  if (!activeSession) {
    return (
      <SetupScreen
        onSessionCreated={(session, resume) => {
          setActiveResume(resume);
          setActiveSession(session);
        }}
      />
    );
  }

  return (
    <ChatWorkspace
      initialSession={activeSession}
      resume={activeResume}
      onResetSession={() => {
        setActiveSession(null);
      }}
    />
  );
}
