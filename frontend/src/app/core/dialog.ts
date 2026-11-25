import { ChangeDetectorRef } from '@angular/core';

/**
 * Opens a modal dialog once its content shows the state that was set just before (fields with their values,
 * the name in a question). The only place that calls `showModal()` (ESLint checks this).
 *
 * Without the render first, the dialog is visible one render earlier than its content: a prefilled field is
 * still empty, and what is typed in that moment is mixed with the value that arrives late. «0» typed over «26»
 * became «260»; that made the E2E test of DRV-21 fail now and then.
 */
export function showModal(dialog: HTMLDialogElement | undefined, changes: ChangeDetectorRef): void {
  if (!dialog || dialog.open) return;
  changes.detectChanges();
  dialog.showModal();
}
