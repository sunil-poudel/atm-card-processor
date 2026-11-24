import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { UsersApiService } from './users-api.service';
import { UsersListComponent } from './users-list.component';

describe('UsersListComponent', () => {
  let component: UsersListComponent;
  let fixture: ComponentFixture<UsersListComponent>;
  let usersApiSpy: jasmine.SpyObj<UsersApiService>;

  const mockUsers = [
    {
      id: 'u1',
      email: 'admin@minicard.local',
      displayName: 'System Admin',
      role: 'ADMIN' as const,
      enabled: true,
      locked: false,
      createdAt: '2026-01-01T00:00:00Z',
      version: 0,
    },
    {
      id: 'u2',
      email: 'analyst@minicard.local',
      displayName: 'Alice Analyst',
      role: 'ANALYST' as const,
      enabled: false,
      locked: true,
      createdAt: '2026-02-01T00:00:00Z',
      version: 1,
    },
  ];

  beforeEach(async () => {
    usersApiSpy = jasmine.createSpyObj<UsersApiService>('UsersApiService', ['listUsers']);
    usersApiSpy.listUsers.and.returnValue(
      of({
        content: mockUsers,
        page: { number: 0, size: 20, totalElements: 2, totalPages: 1 },
      }),
    );

    await TestBed.configureTestingModule({
      imports: [UsersListComponent],
      providers: [provideNoopAnimations(), { provide: UsersApiService, useValue: usersApiSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(UsersListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load users on init', () => {
    expect(component).toBeTruthy();
    expect(usersApiSpy.listUsers).toHaveBeenCalledWith(0, 20, 'email,asc');
    expect(component.users().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('should render user display names and emails', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('System Admin');
    expect(el.textContent).toContain('admin@minicard.local');
    expect(el.textContent).toContain('Alice Analyst');
  });

  it('should open create dialog when Add User button is clicked', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(null),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openCreateDialog();
    expect(spy).toHaveBeenCalled();
  });

  it('should open edit dialog when openEditDialog is called', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const spy = spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of(null),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openEditDialog(mockUsers[0]);
    expect(spy).toHaveBeenCalled();
  });

  it('should render error state on API failure', () => {
    usersApiSpy.listUsers.and.returnValue(throwError(() => new Error('Network error')));
    component.loadUsers();
    fixture.detectChanges();

    expect(component.error()).toBeTruthy();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Failed to load users');
  });
});
