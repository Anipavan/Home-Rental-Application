import * as React from "react";
import type { ToastProps } from "@/components/ui/toast";

type ToasterToast = ToastProps & {
  id: string;
  title?: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  /**
   * Optional dedupe key. If a new toast is fired with the same key
   * as an existing one, the existing toast is dismissed and removed
   * before the new one is added — so rapid-fire calls (e.g. wrong
   * password repeatedly) don't stack a queue of identical messages.
   * Toasts without a key behave as before (always append).
   */
  dedupeKey?: string;
};

const TOAST_LIMIT = 4;
const TOAST_REMOVE_DELAY = 4000;

type Action =
  | { type: "ADD"; toast: ToasterToast }
  | { type: "UPDATE"; toast: Partial<ToasterToast> & { id: string } }
  | { type: "DISMISS"; id?: string }
  | { type: "REMOVE"; id?: string };

interface State {
  toasts: ToasterToast[];
}

const listeners: Array<(state: State) => void> = [];
let memoryState: State = { toasts: [] };

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "ADD":
      return {
        toasts: [action.toast, ...state.toasts].slice(0, TOAST_LIMIT),
      };
    case "UPDATE":
      return {
        toasts: state.toasts.map((t) =>
          t.id === action.toast.id ? { ...t, ...action.toast } : t,
        ),
      };
    case "DISMISS": {
      return {
        toasts: state.toasts.map((t) =>
          action.id === undefined || t.id === action.id
            ? { ...t, open: false }
            : t,
        ),
      };
    }
    case "REMOVE":
      if (action.id === undefined) return { toasts: [] };
      return { toasts: state.toasts.filter((t) => t.id !== action.id) };
  }
}

function dispatch(action: Action) {
  memoryState = reducer(memoryState, action);
  listeners.forEach((l) => l(memoryState));
}

let count = 0;
function genId() {
  count = (count + 1) % Number.MAX_SAFE_INTEGER;
  return count.toString();
}

type ToastInput = Omit<ToasterToast, "id">;

export function toast(props: ToastInput) {
  const id = genId();
  const update = (next: Partial<ToasterToast>) =>
    dispatch({ type: "UPDATE", toast: { ...next, id } });
  const dismiss = () => dispatch({ type: "DISMISS", id });

  // Dedupe: if the caller supplied a key, remove any existing
  // toast(s) with the same key first. Use REMOVE (not DISMISS) so
  // the new toast doesn't share screen real-estate with the
  // outgoing one during its close animation — repeat-press cases
  // (wrong password again, network blip retry, etc.) should feel
  // like a single message that just refreshes.
  if (props.dedupeKey) {
    for (const existing of memoryState.toasts) {
      if (existing.dedupeKey === props.dedupeKey) {
        dispatch({ type: "REMOVE", id: existing.id });
      }
    }
  }

  dispatch({
    type: "ADD",
    toast: {
      ...props,
      id,
      open: true,
      onOpenChange: (open) => {
        if (!open) dismiss();
      },
    },
  });

  setTimeout(() => dispatch({ type: "REMOVE", id }), TOAST_REMOVE_DELAY);
  return { id, dismiss, update };
}

export function useToast() {
  const [state, setState] = React.useState<State>(memoryState);
  React.useEffect(() => {
    listeners.push(setState);
    return () => {
      const i = listeners.indexOf(setState);
      if (i > -1) listeners.splice(i, 1);
    };
  }, []);
  return {
    ...state,
    toast,
    dismiss: (id?: string) => dispatch({ type: "DISMISS", id }),
  };
}
