/**
 * Shared class strings for the recurring UI primitives.
 *
 * These used to be copy-pasted per component with drifting sizes (28px / 32px /
 * 36px / 40px control heights all coexisting), which is what made the interface
 * feel cramped and the buttons hard to tell apart. Keeping every control here
 * means one place to tune the whole scale, and every button in the app is the
 * same height and weight as its peers.
 *
 * ---------------------------------------------------------------------------
 * Composition rule: only combine tokens that set *different* properties.
 *
 * Tailwind resolves two utilities that set the same property by their order in
 * the generated stylesheet, NOT by their order in the class attribute. So
 * `BTN + border-[var(--danger)]` is a coin flip, while `BTN_BASE + SIZE_LG +
 * SCHEME_INVERT` is safe because the three pieces touch geometry, colour and
 * nothing at all respectively. When you need a button that is not in the list
 * below, compose BASE + one SIZE_* + one SCHEME_* - never append a second
 * scheme or size to a ready-made button.
 *
 * Width and flex modifiers (`w-full`, `flex-1`) are always safe to append,
 * because no variant here sets them.
 * ---------------------------------------------------------------------------
 *
 * Scale: MD = 44px (the standard target), LG = 48px (the reading controls),
 * SM = 40px (repeated rows only). Labels are 13px uppercase, fields 15px.
 */

/**
 * Behaviour and typography. No geometry, no colour - safe to build on.
 * The label never wraps: every BTN_* has a fixed height, so a label that wrapped
 * to a second line would spill out of the button's own box.
 */
export const BTN_BASE =
  'inline-flex items-center justify-center gap-2 border font-semibold uppercase tracking-[0.08em] whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-40';

/* Geometry. One padding value per size, so nothing can be half-overridden. */
const MD = 'h-11 px-4 text-[13px]';
const MD_SQUARE = 'h-11 w-11';
const LG = 'h-12 px-5 text-[14px]';
const SM = 'h-10 px-3 text-[12px]';
const SM_SQUARE = 'h-10 w-10';

/* Colour schemes - each one is a complete, self-consistent set. */
const SCHEME_NEUTRAL =
  'border-[var(--border-color)] text-[var(--text-main)] hover:bg-[var(--bg-hover)]';
const SCHEME_INVERT =
  'border-[var(--text-strong)] bg-[var(--text-strong)] text-[var(--bg-surface)] hover:opacity-85';
const SCHEME_ACCENT =
  'border-[var(--accent)] bg-[var(--accent)] text-[var(--bg-surface)] hover:opacity-85';
const SCHEME_ACCENT_SOFT =
  'border-[var(--accent-border)] bg-[var(--accent-soft)] text-[var(--accent)] hover:bg-[var(--bg-hover)]';
const SCHEME_SUCCESS =
  'border-[var(--success)] text-[var(--success)] hover:bg-[var(--bg-hover)]';
const SCHEME_HIGHLIGHT =
  'border-[var(--highlight-border)] bg-[var(--highlight-bg)] text-[var(--highlight-text)] hover:opacity-90';
const SCHEME_DANGER_GHOST =
  'border-[var(--border-color)] text-[var(--text-muted)] hover:border-[var(--danger)] hover:text-[var(--danger)]';

/* ------------------------------ Ready-made buttons ------------------------ */

/** Standard bordered control: header actions, drawer footer, form submits. */
export const BTN = `${BTN_BASE} ${MD} ${SCHEME_NEUTRAL}`;

/** Inverted fill: the single most important action in a view. */
export const BTN_PRIMARY = `${BTN_BASE} ${MD} ${SCHEME_INVERT}`;

/** Accent fill, for the AI action which has its own colour identity. */
export const BTN_ACCENT = `${BTN_BASE} ${MD} ${SCHEME_ACCENT}`;

/** Tinted accent, for secondary-but-emphasised actions. */
export const BTN_ACCENT_SOFT = `${BTN_BASE} ${MD} ${SCHEME_ACCENT_SOFT}`;

/** "Mark as known" / "mark as a new word" in the definition panel footer. */
export const BTN_SUCCESS = `${BTN_BASE} ${MD} ${SCHEME_SUCCESS}`;
export const BTN_HIGHLIGHT = `${BTN_BASE} ${MD} ${SCHEME_HIGHLIGHT}`;

/**
 * Retry action sitting on the amber alert surface: it needs the accent border
 * and text but a solid background, or it would vanish into the alert it lives in.
 */
export const BTN_ON_HIGHLIGHT = `${BTN_BASE} ${MD} border-[var(--highlight-border)] bg-[var(--bg-surface)] text-[var(--highlight-text)] hover:bg-[var(--bg-hover)]`;

/** Borderless icon control for panel headers (close / settings). */
export const BTN_GHOST =
  'inline-flex h-11 w-11 shrink-0 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)]';

/** Destructive affordance: quiet until hovered, then unmistakably red. */
export const BTN_DANGER_ICON = `${BTN_BASE} ${MD_SQUARE} ${SCHEME_DANGER_GHOST}`;

/** Compact pair for repeated rows (the vocabulary list). */
export const BTN_SM = `${BTN_BASE} ${SM} ${SCHEME_NEUTRAL}`;
export const BTN_SM_ICON = `${BTN_BASE} ${SM_SQUARE} ${SCHEME_NEUTRAL}`;
export const BTN_SM_DANGER_ICON = `${BTN_BASE} ${SM_SQUARE} ${SCHEME_DANGER_GHOST}`;

/** The reading page-turn buttons: the largest target, and the most-used. */
export const BTN_NAV = `${BTN_BASE} ${LG} gap-2.5`;
export const SCHEME_NAV_PREV =
  'border-[var(--border-color)] text-[var(--text-main)] enabled:hover:bg-[var(--bg-hover)]';
export const SCHEME_NAV_NEXT =
  'border-[var(--text-strong)] bg-[var(--text-strong)] text-[var(--bg-surface)] enabled:hover:opacity-85';

/* --------------------------------- Form fields ---------------------------- */

/** Text inputs and textareas, shared by all three forms. */
export const FIELD =
  'w-full border border-[var(--border-color)] bg-[var(--bg-main)] px-3.5 py-2.5 text-[15px] text-[var(--text-main)] placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]';

/** Field label / eyebrow heading above a block of content. */
export const SECTION_LABEL =
  'text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--text-muted)]';

/**
 * Segmented control: a row of mutually exclusive options inside one border.
 * The box supplies the outer frame; each segment carries its own right border as
 * the divider that makes its hit area visible (and drops it on the last one, so
 * the frame stays 1px). Carries no padding or size of its own - the axis differs
 * per use (square swatches in the definition panel, wider text segments in the
 * header theme switch and the vocabulary filters).
 */
export const SEGMENT_BOX = 'flex border border-[var(--border-color)]';

export const SEGMENT =
  'inline-flex items-center justify-center gap-1.5 border-r border-[var(--border-color)] text-[13px] font-semibold whitespace-nowrap transition-colors last:border-r-0';

/**
 * The two states of a segment.
 *
 * Both carry a pressed style: `hover:` never fires on a touch screen, so without
 * one a tap gives no feedback at all until the state itself flips - the control
 * reads as dead for as long as the finger is down.
 */
export const SEGMENT_ON = 'bg-[var(--text-strong)] text-[var(--bg-surface)] active:opacity-85';
export const SEGMENT_OFF =
  'text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-strong)] active:bg-[var(--bg-active)] active:text-[var(--text-strong)]';

/**
 * Small bordered status chip: a word's proficiency in the vocabulary list, the
 * lookup status at the top of the definition panel. Fixed height like every
 * other control, so the same rule applies - the label stays on one line and the
 * chip keeps its own width instead of being squeezed by whatever sits next to it.
 * Colour comes from the caller (the level background, or the status colour).
 */
export const BADGE =
  'inline-flex shrink-0 items-center border px-2 py-1 text-[11px] font-semibold tracking-[0.12em] whitespace-nowrap';
