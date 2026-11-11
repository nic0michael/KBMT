import { Component } from '@angular/core';

@Component({
  selector: 'app-database-management',
  template: `
    <section class="database-management" aria-labelledby="database-management-heading">
      <h2 id="database-management-heading">Database Backup and Restore</h2>
      <p>To make a dump, this needs to be done by DevOps from inside the container. Run these commands on the server:</p>
      <pre><code>docker exec -it postgres sh
cd /var/lib/postgresql/scripts</code></pre>

      <h3>Make a database dump</h3>
      <p>To make a database dump, run this command:</p>
      <pre><code>./make-db-dump.sh</code></pre>

      <h3>Restore from dump</h3>
      <p>To restore from a dump, list the available dumps, choose the dump filename, then pass it to the restore script. This is an example listing and command:</p>
      <pre><code>ls -l ../backup

# Example output:
-rw-r--r-- 1 root root 20183 Nov 11 13:00 kbmt_n8n_20261111130016.dump

# Replace the example filename with the chosen dump:
./restore-db-from.sh kbmt_n8n_20261111130016.dump</code></pre>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .database-management { padding: 22px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); color: var(--ink-soft); }
    h2 { margin: 0 0 12px; color: var(--ink); font-size: 18px; }
    h3 { margin: 24px 0 8px; color: var(--ink); font-size: 14px; }
    p { margin: 8px 0; font-size: 12px; line-height: 1.6; }
    pre { overflow-x: auto; padding: 14px; border: 1px solid var(--line); border-radius: 7px; background: var(--surface-raised, #f7f8fa); color: var(--ink); font-size: 12px; line-height: 1.6; }
    code { font-family: ui-monospace, Consolas, monospace; }
    @media (max-width: 560px) { .database-management { padding: 15px; } }
  `],
})
export class DatabaseManagementComponent {}
