import { Component, computed, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

type Permission = 'USER_ACCESS' | 'ADMINISTRATOR_USER_ACCESS' | 'NO_ACCESS';
type Theme = 'light' | 'gray' | 'black';
type Page = 'library' | 'reader' | 'admin' | 'users';

interface UserResponse {
  userID: string;
  permissions: Permission[];
}

interface LoginResponse extends UserResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

interface KnowledgeBase {
  id: string;
  title: string;
  version: string;
  description: string | null;
  contentType: string;
  folderNameVersion: string;
  author: string | null;
  emailAddress: string | null;
  tableOfContents: string | null;
  isActive: boolean;
}

interface KnowledgeBaseForm {
  title: string;
  folderNameVersion: string;
  version: string;
  contentType: string;
  description: string;
  author: string;
  emailAddress: string;
  tableOfContents: string;
  isActive: boolean;
}

interface KnowledgeBasePage {
  id: string;
  title: string;
  createdAt: string;
}

const EMPTY_KNOWLEDGE_BASE: KnowledgeBaseForm = {
  title: '', folderNameVersion: '', version: '', contentType: 'Free',
  description: '', author: '', emailAddress: '', tableOfContents: '', isActive: true,
};

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  host: { '[attr.data-theme]': 'theme()' },
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly user = signal<UserResponse | null>(null);
  protected readonly registeredUsers = signal<UserResponse[]>([]);
  protected readonly knowledgeBases = signal<KnowledgeBase[]>([]);
  protected readonly managedKnowledgeBases = signal<KnowledgeBase[]>([]);
  protected readonly selectedKnowledgeBase = signal<KnowledgeBase | null>(null);
  protected readonly knowledgeBasePages = signal<KnowledgeBasePage[]>([]);
  protected readonly selectedPage = signal<KnowledgeBasePage | null>(null);
  protected readonly markdownContent = signal('');
  protected readonly page = signal<Page>('library');
  protected readonly theme = signal<Theme>(this.readTheme());
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly successMessage = signal('');
  protected readonly showPassword = signal(false);
  protected readonly showUpdatedUserPassword = signal(false);
  protected readonly showUpdatedUserPasswordConfirm = signal(false);
  protected readonly search = signal('');
  protected readonly hasReaderAccess = computed(() => this.user()?.permissions.some(
    permission => permission === 'USER_ACCESS' || permission === 'ADMINISTRATOR_USER_ACCESS',
  ) ?? false);
  protected readonly isAdministrator = computed(() => this.user()?.permissions.includes('ADMINISTRATOR_USER_ACCESS') ?? false);
  protected readonly filteredKnowledgeBases = computed(() => {
    const query = this.search().trim().toLocaleLowerCase();
    if (!query) return this.knowledgeBases();
    return this.knowledgeBases().filter(item =>
      [item.title, item.version, item.description, item.contentType]
        .some(value => value?.toLocaleLowerCase().includes(query)),
    );
  });

  protected userID = '';
  protected password = '';
  protected newUserID = '';
  protected newUserPassword = '';
  protected editUserID = '';
  protected updatedUserID = '';
  protected updatedUserPassword = '';
  protected updatedUserPasswordConfirm = '';
  protected knowledgeBaseForm: KnowledgeBaseForm = { ...EMPTY_KNOWLEDGE_BASE };
  protected selectedKnowledgeBaseID = '';
  protected selectedUploadKnowledgeBaseID = '';
  protected selectedReaderKnowledgeBaseID = '';
  protected markdownFile: File | null = null;
  protected knowledgeBaseEditForm: KnowledgeBaseForm = { ...EMPTY_KNOWLEDGE_BASE };
  protected targetUserID = '';
  protected permissionToChange: Permission = 'USER_ACCESS';
  private authorization = '';

  constructor(private readonly http: HttpClient) {}

  protected async signIn(): Promise<void> {
    this.clearMessages();
    const userID = this.userID.trim();
    const password = this.password;
    if (!userID || !password) {
      this.errorMessage.set('Enter your user ID and password.');
      return;
    }
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.http.post<LoginResponse>('/api/users/login', { userID, password }));
      if (!result) throw new Error('The server returned an empty login response.');
      this.authorization = `${result.tokenType} ${result.accessToken}`;
      this.user.set(result);
      this.password = '';
      if (this.hasReaderAccess()) {
        this.page.set('library');
        await this.loadKnowledgeBases();
        if (!this.errorMessage()) this.successMessage.set(`Welcome back, ${result.userID}.`);
      } else {
        this.successMessage.set('Your account is valid, but it has no application access yet. Ask an administrator to grant access.');
      }
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Sign-in failed. Check your credentials and try again.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async signOut(): Promise<void> {
    let logoutError = '';
    try {
      await firstValueFrom(this.http.post<void>('/api/users/logout', {}));
    } catch (error) {
      logoutError = this.describeError(error, 'The server could not clear the login cookie.');
    }
    this.authorization = '';
    this.user.set(null);
    this.registeredUsers.set([]);
    this.knowledgeBases.set([]);
    this.managedKnowledgeBases.set([]);
    this.selectedKnowledgeBase.set(null);
    this.userID = '';
    this.password = '';
    this.page.set('library');
    this.clearMessages();
    if (logoutError) this.errorMessage.set(logoutError);
  }

  protected async loadKnowledgeBases(): Promise<void> {
    this.clearMessages();
    this.loading.set(true);
    try {
      const list = await firstValueFrom(this.http.get<KnowledgeBase[]>('/api/knowledge-bases', { headers: this.authHeaders() }));
      this.knowledgeBases.set(list ?? []);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not load Knowledge Bases.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async loadManagedKnowledgeBases(): Promise<void> {
    if (!this.isAdministrator()) return;
    this.clearMessages();
    this.loading.set(true);
    try {
      const list = await firstValueFrom(this.http.get<KnowledgeBase[]>(
        '/api/knowledge-bases/management', { headers: this.authHeaders() },
      ));
      this.managedKnowledgeBases.set(list ?? []);
      this.knowledgeBases.set((list ?? []).filter(item => item.isActive));
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not load Knowledge Bases for management.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async loadUsers(): Promise<void> {
    if (!this.isAdministrator()) return;
    this.loading.set(true);
    try {
      const users = await firstValueFrom(this.http.get<UserResponse[]>('/api/users', { headers: this.authHeaders() }));
      this.registeredUsers.set(users ?? []);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not load user accounts.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected openKnowledgeBase(item: KnowledgeBase): void {
    this.selectedKnowledgeBase.set(item);
    this.selectedReaderKnowledgeBaseID = item.id;
    void this.loadPages(item.id);
    this.page.set('reader');
    this.clearMessages();
  }

  protected async changeReaderKnowledgeBase(id: string): Promise<void> {
    this.selectedReaderKnowledgeBaseID = id;
    this.selectedKnowledgeBase.set(this.knowledgeBases().find(item => item.id === id) ?? null);
    await this.loadPages(id);
  }

  protected async loadPages(knowledgeBaseID: string): Promise<void> {
    if (!knowledgeBaseID) return;
    this.knowledgeBasePages.set([]);
    this.selectedPage.set(null);
    this.markdownContent.set('');
    try {
      const pages = await firstValueFrom(this.http.get<KnowledgeBasePage[]>(
        `/api/knowledge-bases/${encodeURIComponent(knowledgeBaseID)}/pages`, { headers: this.authHeaders() },
      ));
      this.knowledgeBasePages.set(pages ?? []);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not load pages for this Knowledge Base.'));
    }
  }

  protected async openPage(pageID: string): Promise<void> {
    const page = this.knowledgeBasePages().find(item => item.id === pageID);
    this.selectedPage.set(page ?? null);
    this.markdownContent.set('');
    if (!page) return;
    try {
      const content = await firstValueFrom(this.http.get(
        `/api/knowledge-bases/${encodeURIComponent(this.selectedReaderKnowledgeBaseID)}/pages/${encodeURIComponent(page.id)}`,
        { headers: this.authHeaders(), responseType: 'text' },
      ));
      this.markdownContent.set(content);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not load the selected page.'));
    }
  }

  protected selectMarkdownFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.markdownFile = input.files?.[0] ?? null;
  }

  protected async uploadMarkdownPage(): Promise<void> {
    if (!this.isAdministrator() || !this.selectedUploadKnowledgeBaseID || !this.markdownFile) {
      this.errorMessage.set('Select a Knowledge Base and a Markdown file.');
      return;
    }
    this.clearMessages();
    this.loading.set(true);
    const form = new FormData();
    form.append('file', this.markdownFile);
    try {
      const saved = await firstValueFrom(this.http.post<KnowledgeBasePage>(
        `/api/knowledge-bases/${encodeURIComponent(this.selectedUploadKnowledgeBaseID)}/pages`, form,
        { headers: this.authHeaders() },
      ));
      this.successMessage.set(`${saved.title} was uploaded successfully.`);
      this.markdownFile = null;
      this.selectedUploadKnowledgeBaseID = '';
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not upload the Markdown page.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async registerKnowledgeBase(): Promise<void> {
    if (!this.isAdministrator()) return;
    this.clearMessages();
    this.loading.set(true);
    try {
      const created = await firstValueFrom(this.http.post<KnowledgeBase>('/api/knowledge-bases', this.knowledgeBaseForm, {
        headers: this.authHeaders(),
      }));
      if (created) {
        this.managedKnowledgeBases.update(items => [created, ...items]);
        if (created.isActive) this.knowledgeBases.update(items => [created, ...items]);
        this.knowledgeBaseForm = { ...EMPTY_KNOWLEDGE_BASE };
        this.successMessage.set(`${created.title} was registered${created.isActive ? '' : ' as inactive'} successfully.`);
      }
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not register this Knowledge Base.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected selectKnowledgeBaseForEdit(id: string): void {
    this.selectedKnowledgeBaseID = id;
    const item = this.managedKnowledgeBases().find(knowledgeBase => knowledgeBase.id === id);
    this.knowledgeBaseEditForm = item ? {
      title: item.title,
      folderNameVersion: item.folderNameVersion,
      version: item.version,
      contentType: item.contentType,
      description: item.description ?? '',
      author: item.author ?? '',
      emailAddress: item.emailAddress ?? '',
      tableOfContents: item.tableOfContents ?? '',
      isActive: item.isActive,
    } : { ...EMPTY_KNOWLEDGE_BASE };
  }

  protected async updateKnowledgeBase(): Promise<void> {
    if (!this.isAdministrator() || !this.selectedKnowledgeBaseID) return;
    const existing = this.managedKnowledgeBases().find(item => item.id === this.selectedKnowledgeBaseID);
    if (!existing) {
      this.errorMessage.set('Select an active Knowledge Base to edit.');
      return;
    }
    if (existing.isActive && !this.knowledgeBaseEditForm.isActive
      && !window.confirm(`Deactivate "${existing.title}"? It will be hidden from listings and retained in the database.`)) return;
    this.clearMessages();
    this.loading.set(true);
    try {
      const updated = await firstValueFrom(this.http.put<KnowledgeBase>(
        `/api/knowledge-bases/${encodeURIComponent(existing.id)}`,
        this.knowledgeBaseEditForm,
        { headers: this.authHeaders() },
      ));
      if (updated.isActive) {
        this.knowledgeBases.update(items => items.map(item => item.id === updated.id ? updated : item));
        if (!this.knowledgeBases().some(item => item.id === updated.id)) {
          this.knowledgeBases.update(items => [updated, ...items]);
        }
      } else {
        this.knowledgeBases.update(items => items.filter(item => item.id !== updated.id));
      }
      this.managedKnowledgeBases.update(items => items.map(item => item.id === updated.id ? updated : item));
      if (this.selectedKnowledgeBase()?.id === updated.id) {
        this.selectedKnowledgeBase.set(updated.isActive ? updated : null);
        if (!updated.isActive) this.page.set('library');
      }
      this.selectKnowledgeBaseForEdit('');
      this.successMessage.set(`${updated.title} was updated successfully.`);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not update this Knowledge Base.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async deactivateKnowledgeBase(item: KnowledgeBase): Promise<void> {
    if (!this.isAdministrator() || !item.isActive) return;
    if (!window.confirm(`Deactivate "${item.title}"? It will be hidden from listings and retained in the database.`)) return;
    this.clearMessages();
    this.loading.set(true);
    const request = {
      title: item.title,
      folderNameVersion: item.folderNameVersion,
      version: item.version,
      author: item.author,
      emailAddress: item.emailAddress,
      tableOfContents: item.tableOfContents,
      description: item.description,
      contentType: item.contentType,
      isActive: false,
    };
    try {
      const updated = await firstValueFrom(this.http.put<KnowledgeBase>(`/api/knowledge-bases/${encodeURIComponent(item.id)}`, request, {
        headers: this.authHeaders(),
      }));
      this.knowledgeBases.update(items => items.filter(knowledgeBase => knowledgeBase.id !== item.id));
      this.managedKnowledgeBases.update(items => items.map(knowledgeBase => knowledgeBase.id === item.id ? updated : knowledgeBase));
      if (this.selectedKnowledgeBase()?.id === item.id) {
        this.selectedKnowledgeBase.set(null);
        this.page.set('library');
      }
      this.successMessage.set(`${item.title} was deactivated and remains stored.`);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not deactivate this Knowledge Base.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async changePermission(action: 'grant' | 'revoke'): Promise<void> {
    if (!this.isAdministrator()) return;
    const userID = this.targetUserID.trim();
    if (!userID) {
      this.errorMessage.set('Enter the user ID to update.');
      return;
    }
    this.clearMessages();
    this.loading.set(true);
    const method = action === 'grant' ? 'put' : 'delete';
    try {
      const updatedUser = await firstValueFrom(this.http.request<UserResponse>(method, `/api/users/${encodeURIComponent(userID)}/permissions`, {
        headers: this.authHeaders(),
        body: { permission: this.permissionToChange },
      }));
      this.registeredUsers.update(users => users.map(user => user.userID === updatedUser.userID ? updatedUser : user));
      this.successMessage.set(`${this.permissionToChange.replaceAll('_', ' ')} ${action === 'grant' ? 'granted to' : 'revoked from'} ${userID}.`);
      this.targetUserID = '';
    } catch (error) {
      this.errorMessage.set(this.describeError(error, `Could not ${action} permission.`));
    } finally {
      this.loading.set(false);
    }
  }

  protected async createUser(): Promise<void> {
    if (!this.isAdministrator()) return;
    const userID = this.newUserID.trim();
    if (!userID || !this.newUserPassword) {
      this.errorMessage.set('Enter a user ID and password.');
      return;
    }
    this.clearMessages();
    this.loading.set(true);
    try {
      const createdUser = await firstValueFrom(this.http.post<UserResponse>('/api/users/register', {
        userID,
        password: this.newUserPassword,
      }, { headers: this.authHeaders() }));
      this.registeredUsers.update(users => [createdUser, ...users.filter(user => user.userID !== userID)]);
      this.targetUserID = createdUser.userID;
      this.newUserID = '';
      this.newUserPassword = '';
      this.successMessage.set(`${userID} was created with no access. Grant a permission to enable the account.`);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not create this user.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async editUser(): Promise<void> {
    if (!this.isAdministrator()) return;
    const currentUserID = this.editUserID.trim();
    const updatedUserID = this.updatedUserID.trim();
    if (!currentUserID || !updatedUserID) {
      this.errorMessage.set('Enter the current user ID and the new user ID.');
      return;
    }
    if (this.updatedUserPassword !== this.updatedUserPasswordConfirm) {
      this.errorMessage.set('The new password and confirmation do not match.');
      return;
    }
    this.clearMessages();
    this.loading.set(true);
    const request: { userID: string; password?: string } = { userID: updatedUserID };
    if (this.updatedUserPassword) request.password = this.updatedUserPassword;
    try {
      const updatedUser = await firstValueFrom(this.http.put<UserResponse>(`/api/users/${encodeURIComponent(currentUserID)}`, request, {
        headers: this.authHeaders(),
      }));
      if (this.targetUserID === currentUserID) this.targetUserID = updatedUserID;
      this.registeredUsers.update(users => [updatedUser, ...users.filter(user => user.userID !== currentUserID)]);
      this.editUserID = '';
      this.updatedUserID = '';
      this.updatedUserPassword = '';
      this.updatedUserPasswordConfirm = '';
      this.successMessage.set(`${currentUserID} was updated to ${updatedUserID}. Existing permissions were preserved.`);
    } catch (error) {
      this.errorMessage.set(this.describeError(error, 'Could not update this user.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected setTheme(value: string): void {
    if (value !== 'light' && value !== 'gray' && value !== 'black') return;
    this.theme.set(value);
    localStorage.setItem('togafmate-theme', value);
  }

  protected setThemeFromEvent(event: Event): void {
    this.setTheme((event.target as HTMLSelectElement).value);
  }

  protected updateSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected togglePasswordVisibility(): void {
    this.showPassword.update(visible => !visible);
  }

  protected toggleUpdatedUserPasswordVisibility(): void {
    this.showUpdatedUserPassword.update(visible => !visible);
  }

  protected toggleUpdatedUserPasswordConfirmVisibility(): void {
    this.showUpdatedUserPasswordConfirm.update(visible => !visible);
  }

  protected navigate(page: Page): void {
    const isAdminPage = page === 'admin' || page === 'users';
    if (isAdminPage && !this.isAdministrator()) return;
    if (!isAdminPage && !this.hasReaderAccess()) return;
    this.page.set(page);
    this.selectedKnowledgeBase.set(null);
    this.clearMessages();
    if (page === 'library') void this.loadKnowledgeBases();
    if (page === 'admin') void this.loadManagedKnowledgeBases();
    if (page === 'users') void this.loadUsers();
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: this.authorization });
  }

  private describeError(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { detail?: string; message?: string; title?: string } | null;
      if (error.status === 0) return 'The API is unavailable. Check that the backend is running and try again.';
      return body?.detail ?? body?.message ?? body?.title ?? (error.status === 401 ? 'Your credentials were not accepted.' : fallback);
    }
    return error instanceof Error ? error.message : fallback;
  }

  private clearMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  private readTheme(): Theme {
    const saved = typeof localStorage === 'undefined' ? null : localStorage.getItem('togafmate-theme');
    return saved === 'gray' || saved === 'black' ? saved : 'light';
  }
}
