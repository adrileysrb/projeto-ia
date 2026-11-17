import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  it('should show the chat', async () => {
    await TestBed.configureTestingModule({ imports: [App] }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Assistente de transações');
    expect(compiled.querySelector('textarea')).toBeTruthy();
  });
});
