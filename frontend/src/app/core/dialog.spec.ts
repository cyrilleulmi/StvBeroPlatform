import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { showModal } from './dialog';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<dialog #dialog><input #field [value]="value()" /></dialog>`,
})
class Host {
  private readonly changes = inject(ChangeDetectorRef);
  readonly value = signal('');
  readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  /** Like every dialog of the app: set the state, then open. */
  open(value: string): void {
    this.value.set(value);
    showModal(this.dialog().nativeElement, this.changes);
  }
}

describe('showModal', () => {
  function host() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const dialog = component.dialog().nativeElement;
    const seen: string[] = [];
    // Records what the field shows at the moment the dialog becomes visible
    dialog.showModal = () => {
      seen.push(component.field().nativeElement.value);
      dialog.setAttribute('open', '');
    };
    return { component, seen };
  }

  it('shows the dialog with the value that was set just before, not one render later', () => {
    const { component, seen } = host();
    component.open('26');
    expect(seen).toEqual(['26']);
  });

  it('leaves an open dialog alone', () => {
    const { component, seen } = host();
    component.open('26');
    component.open('40');
    expect(seen).toEqual(['26']);
  });

  it('does nothing without a dialog', () => {
    expect(() => showModal(undefined, {} as ChangeDetectorRef)).not.toThrow();
  });
});
