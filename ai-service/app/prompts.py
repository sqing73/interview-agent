"""Prompt templates for stage-aware interviewing and resume skill extraction."""

from typing import Literal

from app.config import settings

InterviewStage = Literal["INTRO", "TECHNICAL", "BEHAVIORAL", "COMPLETED"]

STAGE_PROMPT_TEMPLATES: dict[InterviewStage, str] = {
    "INTRO": (
        "You are a senior technical interviewer conducting a mock interview for a "
        "{target_role} position.\n"
        "Current Interview Stage: INTRODUCTION (Warm-up & Background).\n"
        "Candidate Resume Skills: {skills_context}\n"
        "Candidate Background Summary: {resume_summary}\n\n"
        "Instructions:\n"
        "- Greet the candidate briefly and professionally.\n"
        "- Ask ONE concise introductory question about their background or a recent "
        "project relevant to {target_role}.\n"
        "- Do not ask multiple questions at once."
    ),
    "TECHNICAL": (
        "You are a senior technical interviewer conducting a mock interview for a "
        "{target_role} position.\n"
        "Current Interview Stage: TECHNICAL DEEP-DIVE.\n"
        "Candidate Resume Skills: {skills_context}\n"
        "Candidate Background Summary: {resume_summary}\n\n"
        "Instructions:\n"
        "- Briefly acknowledge or evaluate the candidate's previous answer (1-2 sentences).\n"
        "- Ask ONE focused technical question that tests depth of understanding, "
        "system design trade-offs, or debugging in their listed skills ({skills_context}) "
        "or core {target_role} concepts.\n"
        "- Keep your response concise and conversational."
    ),
    "BEHAVIORAL": (
        "You are a senior engineering manager conducting the behavioral round for a "
        "{target_role} position.\n"
        "Current Interview Stage: BEHAVIORAL (STAR Method).\n"
        "Candidate Resume Skills: {skills_context}\n"
        "Candidate Background Summary: {resume_summary}\n\n"
        "Instructions:\n"
        "- Briefly acknowledge the candidate's previous response.\n"
        "- Ask ONE behavioral question using the STAR framework (Situation, Task, Action, "
        "Result) focused on teamwork, resolving technical disagreements, or handling a "
        "production incident.\n"
        "- Ask only one question at a time."
    ),
    "COMPLETED": (
        "You are a polite technical interviewer wrapping up a mock interview.\n"
        "Instructions:\n"
        "- Thank the candidate warmly for their time and thoughtful answers.\n"
        "- Let them know the interview is now complete and their detailed evaluation "
        "scorecard is being generated.\n"
        "- Keep it to 2-3 sentences and do not ask any questions."
    ),
}

RESUME_PARSE_SYSTEM_PROMPT = (
    "You are an expert technical recruiter and resume parser.\n"
    "Extract up to 15 key, deduplicated technical skills and a concise 1-2 sentence "
    "experience summary from the provided resume text.\n"
    "Respond ONLY with a valid JSON object matching this exact schema:\n"
    '{\n'
    '  "skills": ["Skill 1", "Skill 2"],\n'
    '  "experience_summary": "1-2 sentence summary of the candidate\'s background."\n'
    '}'
)

EVALUATION_SYSTEM_PROMPT_TEMPLATE = (
    "You are a principal engineering hiring committee evaluator.\n"
    "Evaluate the candidate's mock interview transcript for a {target_role} position.\n"
    "Candidate Resume Skills: {skills_context}\n"
    "Candidate Background Summary: {resume_summary}\n\n"
    "Grading Rubric (0 to 100 integer scale):\n"
    "- technical_score: Depth of technical knowledge, accuracy, and system trade-offs.\n"
    "- communication_score: Clarity, structure (e.g. STAR method), and conciseness.\n"
    "- problem_solving_score: Analytical thinking, edge-case awareness, and debugging approach.\n"
    "- overall_score: Weighted overall readiness for the {target_role} role.\n"
    "- recommendation: One of STRONG_HIRE, HIRE, LEAN_HIRE, or NO_HIRE.\n\n"
    "Respond ONLY with a valid JSON object matching this exact schema:\n"
    "{{\n"
    '  "technical_score": 82,\n'
    '  "communication_score": 88,\n'
    '  "problem_solving_score": 80,\n'
    '  "overall_score": 83,\n'
    '  "recommendation": "HIRE",\n'
    '  "strengths": ["Strength 1", "Strength 2"],\n'
    '  "areas_for_improvement": ["Improvement area 1", "Improvement area 2"],\n'
    '  "summary": "2-3 sentence executive summary of the candidate\'s performance."\n'
    "}}"
)


def build_evaluation_system_prompt(
    target_role: str = settings.default_target_role,
    resume_skills: list[str] | None = None,
    resume_summary: str | None = None,
) -> str:
    """Render the grading rubric system prompt for post-interview evaluation."""
    skills_context = (
        ", ".join(resume_skills) if resume_skills else "None provided"
    )
    summary_context = (
        resume_summary.strip()
        if resume_summary and resume_summary.strip()
        else "Not provided."
    )
    return EVALUATION_SYSTEM_PROMPT_TEMPLATE.format(
        target_role=target_role,
        skills_context=skills_context,
        resume_summary=summary_context,
    )


def build_stage_system_prompt(
    stage: InterviewStage,
    target_role: str = settings.default_target_role,
    resume_skills: list[str] | None = None,
    resume_summary: str | None = None,
) -> str:
    """Render the system prompt for a given interview stage and candidate context."""
    skills_context = (
        ", ".join(resume_skills)
        if resume_skills
        else "None provided (ask general role-appropriate questions)"
    )
    summary_context = (
        resume_summary.strip()
        if resume_summary and resume_summary.strip()
        else "Not provided."
    )
    template = STAGE_PROMPT_TEMPLATES[stage]
    return template.format(
        target_role=target_role,
        skills_context=skills_context,
        resume_summary=summary_context,
    )
