import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DatabaseManagementComponent } from './database-management';

describe('DatabaseManagementComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DatabaseManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should show DevOps backup and restore instructions without calling the backup API', () => {
    const fixture = TestBed.createComponent(DatabaseManagementComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('done by DevOps from inside the container');
    expect(text).toContain('docker exec -it postgres sh');
    expect(text).toContain('cd /var/lib/postgresql/scripts');
    expect(text).toContain('./make-db-dump.sh');
    expect(text).toContain('ls -l ../backup');
    expect(text).toContain('./restore-db-from.sh kbmt_n8n_20261111130016.dump');
    httpTesting.expectNone('/api/database/backups');
  });
});
