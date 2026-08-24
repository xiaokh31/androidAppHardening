export const ACTIVE_GOVERNANCE_POLICY_SURFACES = Object.freeze([
  "AGENTS.md",
  "HandOff.md",
  "README.md",
  "docs/HANDOFF_SPEC.md",
  "docs/agents/COORDINATOR_AGENT.md",
  "docs/agents/WORKER_AGENT.md",
  "docs/agents/SECURITY_REVIEW_AGENT.md",
  "docs/agents/QA_AGENT.md",
  ".agents/skills/plan-apk-hardening-change/SKILL.md",
  ".agents/skills/implement-apk-postprocessor/SKILL.md",
  ".agents/skills/implement-runtime-protection/SKILL.md",
  ".agents/skills/validate-protected-apk/SKILL.md",
  ".agents/skills/coordinate-project-handoff/SKILL.md",
  ".agents/skills/audit-third-party-skill/SKILL.md",
  ".agents/skills/coordinate-project-handoff/references/handoff-schema.md",
  ".agents/skills/coordinate-project-handoff/assets/worker-handoff-template.md",
]);

export function isActiveGovernancePolicySurface(relativePath) {
  return ACTIVE_GOVERNANCE_POLICY_SURFACES.includes(relativePath);
}
