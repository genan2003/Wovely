import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { ChatComponent } from './chat.component';
import { StorageService } from '../../services/storage.service';
import { provideTestDefaults } from '../../../testing/test-providers';
import { anAuthUser } from '../../../testing/fixtures';

describe('ChatComponent', () => {
  async function setup() {
    TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [...provideTestDefaults(), { provide: StorageService, useValue: { getUser: () => anAuthUser() } }],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ChatComponent);
    fixture.componentRef.setInput('receiverId', 'seller-1');
    fixture.componentRef.setInput('receiverName', 'Ana');
    await fixture.whenStable();
    http.match(() => true).forEach((r) => r.flush([]));
    await fixture.whenStable();
    return { fixture, http, root: fixture.nativeElement as HTMLElement };
  }

  it('clears the input once a message is sent (regression: zoneless left the text in place)', async () => {
    const { fixture, http, root } = await setup();
    fixture.componentInstance.newMessage.set('Is it waterproof?');
    await fixture.whenStable();

    fixture.componentInstance.send();
    http.expectOne((r) => r.method === 'POST').flush({ id: 'm1', senderId: 'buyer-1', receiverId: 'seller-1', content: 'Is it waterproof?' });
    await fixture.whenStable();

    expect(root.querySelector<HTMLInputElement>('#chat-input')?.value).toBe('');
    expect(root.textContent).toContain('Is it waterproof?');
  });

  it('emits closeChat from the close button (regression: it was an input after migration)', async () => {
    const { fixture, root } = await setup();
    let closed = false;
    fixture.componentInstance.closeChat.subscribe(() => (closed = true));
    root.querySelector<HTMLButtonElement>('.chat__close')!.click();
    expect(closed).toBe(true);
  });
});
