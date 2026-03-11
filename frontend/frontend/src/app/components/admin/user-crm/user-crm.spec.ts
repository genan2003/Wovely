import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserCrm } from './user-crm';

describe('UserCrm', () => {
  let component: UserCrm;
  let fixture: ComponentFixture<UserCrm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserCrm],
    }).compileComponents();

    fixture = TestBed.createComponent(UserCrm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
