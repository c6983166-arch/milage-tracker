using System.Diagnostics;
using System.Text.Json;
using Microsoft.Data.Sqlite;

namespace ChromeSiteCleaner;

internal static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}

internal sealed class ChromeProfile
{
    public string Name { get; init; } = "";
    public string HistoryPath { get; init; } = "";
    public override string ToString() => Name;
}

internal sealed class SiteInfo
{
    public string Domain { get; init; } = "";
    public int Visits { get; set; }
    public DateTime LastVisit { get; set; }
}

internal sealed class MainForm : Form
{
    private readonly string _chromeUserData = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Google", "Chrome", "User Data");

    private readonly string _settingsDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "ChromeSiteCleaner");

    private readonly ComboBox profileCombo = new();
    private readonly ComboBox rangeCombo = new();
    private readonly TextBox searchBox = new();
    private readonly Button scanButton = new();
    private readonly DataGridView grid = new();
    private readonly Label countLabel = new();
    private readonly Label statusLabel = new();
    private readonly Button deleteButton = new();
    private readonly HashSet<string> protectedSites = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, SiteInfo> siteData = new(StringComparer.OrdinalIgnoreCase);

    private string ProtectedFile => Path.Combine(_settingsDir, "protected-sites.json");
    private string BackupDir => Path.Combine(_settingsDir, "Backups");

    public MainForm()
    {
        Text = "Chrome Site Cleaner v0.2 TEST";
        StartPosition = FormStartPosition.CenterScreen;
        Size = new Size(1000, 760);
        MinimumSize = new Size(900, 650);
        BackColor = Color.White;
        Font = new Font("Segoe UI", 10F);

        Directory.CreateDirectory(_settingsDir);
        Directory.CreateDirectory(BackupDir);
        LoadProtectedSites();
        BuildUi();
        LoadProfiles();
    }

    private void BuildUi()
    {
        var header = new Panel
        {
            Dock = DockStyle.Top,
            Height = 86,
            BackColor = Color.FromArgb(35, 99, 170)
        };
        Controls.Add(header);

        header.Controls.Add(new Label
        {
            Text = "CHROME SITE CLEANER",
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 22F, FontStyle.Bold),
            AutoSize = true,
            Location = new Point(20, 10)
        });
        header.Controls.Add(new Label
        {
            Text = "Organize Chrome history by website • keep login information protected",
            ForeColor = Color.FromArgb(225, 235, 245),
            AutoSize = true,
            Location = new Point(23, 51)
        });

        var top = new Panel { Dock = DockStyle.Top, Height = 125, Padding = new Padding(18, 10, 18, 4) };
        Controls.Add(top);

        top.Controls.Add(MakeLabel("Chrome Profile", 18, 10));
        profileCombo.DropDownStyle = ComboBoxStyle.DropDownList;
        profileCombo.Location = new Point(18, 34);
        profileCombo.Size = new Size(240, 30);
        top.Controls.Add(profileCombo);

        top.Controls.Add(MakeLabel("History Range", 276, 10));
        rangeCombo.DropDownStyle = ComboBoxStyle.DropDownList;
        rangeCombo.Items.AddRange(new object[] { "Today", "Last 7 Days", "Last 30 Days", "All Time" });
        rangeCombo.SelectedItem = "Last 30 Days";
        rangeCombo.Location = new Point(276, 34);
        rangeCombo.Size = new Size(155, 30);
        top.Controls.Add(rangeCombo);

        top.Controls.Add(MakeLabel("Find Site", 450, 10));
        searchBox.Location = new Point(450, 34);
        searchBox.Size = new Size(240, 30);
        searchBox.TextChanged += (_, _) => RefreshGrid();
        top.Controls.Add(searchBox);

        scanButton.Text = "SCAN HISTORY";
        scanButton.Location = new Point(710, 29);
        scanButton.Size = new Size(155, 40);
        scanButton.BackColor = Color.FromArgb(35, 99, 170);
        scanButton.ForeColor = Color.White;
        scanButton.FlatStyle = FlatStyle.Flat;
        scanButton.Font = new Font("Segoe UI", 10F, FontStyle.Bold);
        scanButton.Click += (_, _) => ScanHistory();
        top.Controls.Add(scanButton);

        top.Controls.Add(new Label
        {
            Text = "TEST BUILD: Deletes selected HISTORY only. Cookies • Passwords • Autofill • Bookmarks are not touched.",
            ForeColor = Color.FromArgb(145, 80, 0),
            Font = new Font("Segoe UI", 9F, FontStyle.Bold),
            AutoSize = true,
            Location = new Point(18, 87)
        });

        var bottom = new Panel { Dock = DockStyle.Bottom, Height = 135, Padding = new Padding(18, 8, 18, 10) };
        Controls.Add(bottom);

        var selectAll = MakeButton("SELECT ALL", 18, 9, 115, 35);
        selectAll.Click += (_, _) => SetChecks(true);
        bottom.Controls.Add(selectAll);

        var clear = MakeButton("CLEAR", 140, 9, 90, 35);
        clear.Click += (_, _) => SetChecks(false);
        bottom.Controls.Add(clear);

        var protect = MakeButton("PROTECT SITE", 245, 9, 130, 35);
        protect.Click += (_, _) => ProtectSelected(true);
        bottom.Controls.Add(protect);

        var unprotect = MakeButton("UNPROTECT", 383, 9, 115, 35);
        unprotect.Click += (_, _) => ProtectSelected(false);
        bottom.Controls.Add(unprotect);

        deleteButton.Text = "DELETE SELECTED HISTORY";
        deleteButton.Location = new Point(515, 8);
        deleteButton.Size = new Size(220, 39);
        deleteButton.BackColor = Color.FromArgb(190, 55, 45);
        deleteButton.ForeColor = Color.White;
        deleteButton.FlatStyle = FlatStyle.Flat;
        deleteButton.Font = new Font("Segoe UI", 10F, FontStyle.Bold);
        deleteButton.Click += (_, _) => DeleteSelectedHistory();
        bottom.Controls.Add(deleteButton);

        var backups = MakeButton("OPEN BACKUPS", 748, 9, 135, 35);
        backups.Click += (_, _) => Process.Start(new ProcessStartInfo("explorer.exe", BackupDir) { UseShellExecute = true });
        bottom.Controls.Add(backups);

        countLabel.Text = "Sites shown: 0";
        countLabel.AutoSize = true;
        countLabel.Location = new Point(18, 60);
        bottom.Controls.Add(countLabel);

        statusLabel.Text = "Ready. Click Scan History.";
        statusLabel.Location = new Point(18, 88);
        statusLabel.Size = new Size(850, 30);
        statusLabel.ForeColor = Color.FromArgb(70, 70, 70);
        statusLabel.AutoEllipsis = true;
        bottom.Controls.Add(statusLabel);

        grid.Dock = DockStyle.Fill;
        grid.BackgroundColor = Color.White;
        grid.BorderStyle = BorderStyle.Fixed3D;
        grid.AllowUserToAddRows = false;
        grid.AllowUserToDeleteRows = false;
        grid.AllowUserToResizeRows = false;
        grid.RowHeadersVisible = false;
        grid.SelectionMode = DataGridViewSelectionMode.FullRowSelect;
        grid.RowTemplate.Height = 30;
        grid.ColumnHeadersHeight = 34;
        grid.AutoGenerateColumns = false;
        grid.Columns.Add(new DataGridViewCheckBoxColumn { HeaderText = "Clean", Width = 60 });
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "Website", AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill, ReadOnly = true });
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "Visits", Width = 85, ReadOnly = true });
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "Last Visit", Width = 175, ReadOnly = true });
        grid.Columns.Add(new DataGridViewTextBoxColumn { HeaderText = "Protected", Width = 95, ReadOnly = true });
        Controls.Add(grid);

        // Docking order: header, top controls, bottom actions, grid fill.
        Controls.SetChildIndex(header, 0);
        Controls.SetChildIndex(top, 1);
        Controls.SetChildIndex(bottom, 2);
        Controls.SetChildIndex(grid, 3);
    }

    private static Label MakeLabel(string text, int x, int y) => new()
    {
        Text = text,
        AutoSize = true,
        Location = new Point(x, y)
    };

    private static Button MakeButton(string text, int x, int y, int w, int h) => new()
    {
        Text = text,
        Location = new Point(x, y),
        Size = new Size(w, h)
    };

    private void LoadProfiles()
    {
        profileCombo.Items.Clear();
        if (!Directory.Exists(_chromeUserData))
        {
            statusLabel.Text = "Google Chrome user data was not found for this Windows account.";
            return;
        }

        foreach (var dir in Directory.GetDirectories(_chromeUserData))
        {
            var name = Path.GetFileName(dir);
            if (!name.Equals("Default", StringComparison.OrdinalIgnoreCase) && !name.StartsWith("Profile ", StringComparison.OrdinalIgnoreCase))
                continue;

            var history = Path.Combine(dir, "History");
            if (File.Exists(history))
                profileCombo.Items.Add(new ChromeProfile { Name = name, HistoryPath = history });
        }

        if (profileCombo.Items.Count > 0)
            profileCombo.SelectedIndex = 0;
        else
            statusLabel.Text = "No Chrome profile containing a History database was found.";
    }

    private DateTime CutoffDate() => rangeCombo.SelectedItem?.ToString() switch
    {
        "Today" => DateTime.Today,
        "Last 7 Days" => DateTime.Now.AddDays(-7),
        "Last 30 Days" => DateTime.Now.AddDays(-30),
        _ => DateTime.MinValue
    };

    private void ScanHistory()
    {
        if (profileCombo.SelectedItem is not ChromeProfile profile)
        {
            MessageBox.Show("No Chrome profile is selected.", Text, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        try
        {
            scanButton.Enabled = false;
            statusLabel.Text = "Scanning Chrome history...";
            Refresh();

            string tempDb = Path.Combine(Path.GetTempPath(), "ChromeSiteCleaner_" + Guid.NewGuid().ToString("N") + ".db");
            CopyFileShared(profile.HistoryPath, tempDb);

            siteData.Clear();
            var cutoff = CutoffDate();
            using (var conn = new SqliteConnection($"Data Source={tempDb};Mode=ReadOnly"))
            {
                conn.Open();
                using var cmd = conn.CreateCommand();
                cmd.CommandText = "SELECT u.url, v.visit_time FROM visits v JOIN urls u ON u.id=v.url ORDER BY v.visit_time DESC;";
                using var reader = cmd.ExecuteReader();
                while (reader.Read())
                {
                    var url = reader.GetString(0);
                    var when = ChromeTimeToLocal(reader.GetInt64(1));
                    if (when < cutoff) continue;
                    var domain = NormalizeDomain(url);
                    if (domain is null) continue;

                    if (!siteData.TryGetValue(domain, out var info))
                    {
                        info = new SiteInfo { Domain = domain, LastVisit = DateTime.MinValue };
                        siteData[domain] = info;
                    }
                    info.Visits++;
                    if (when > info.LastVisit) info.LastVisit = when;
                }
            }
            TryDelete(tempDb);
            RefreshGrid();
            statusLabel.Text = "Scan complete. Login cookies, passwords, autofill and bookmarks remain untouched.";
        }
        catch (Exception ex)
        {
            MessageBox.Show("Chrome history could not be scanned.\r\n\r\n" + ex.Message, Text, MessageBoxButtons.OK, MessageBoxIcon.Error);
            statusLabel.Text = "Scan failed.";
        }
        finally
        {
            scanButton.Enabled = true;
        }
    }

    private void RefreshGrid()
    {
        var search = searchBox.Text.Trim();
        grid.Rows.Clear();
        foreach (var item in siteData.Values.OrderBy(x => x.Domain))
        {
            if (search.Length > 0 && !item.Domain.Contains(search, StringComparison.OrdinalIgnoreCase))
                continue;

            bool isProtected = protectedSites.Contains(item.Domain);
            int i = grid.Rows.Add(false, item.Domain, item.Visits, item.LastVisit.ToString("g"), isProtected ? "YES" : "");
            if (isProtected)
                grid.Rows[i].DefaultCellStyle.BackColor = Color.FromArgb(255, 248, 220);
        }
        countLabel.Text = "Sites shown: " + grid.Rows.Count;
    }

    private void SetChecks(bool value)
    {
        foreach (DataGridViewRow row in grid.Rows)
            row.Cells[0].Value = value;
    }

    private List<string> CheckedDomains(bool includeProtected)
    {
        grid.EndEdit();
        var result = new List<string>();
        foreach (DataGridViewRow row in grid.Rows)
        {
            bool check = row.Cells[0].Value is bool b && b;
            if (!check) continue;
            string domain = Convert.ToString(row.Cells[1].Value) ?? "";
            if (domain.Length == 0) continue;
            if (!includeProtected && protectedSites.Contains(domain)) continue;
            result.Add(domain);
        }
        return result;
    }

    private void ProtectSelected(bool protect)
    {
        var domains = CheckedDomains(true);
        if (domains.Count == 0)
        {
            MessageBox.Show("Select one or more sites first.", Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        foreach (var d in domains)
        {
            if (protect) protectedSites.Add(d);
            else protectedSites.Remove(d);
        }
        SaveProtectedSites();
        RefreshGrid();
        statusLabel.Text = protect ? "Selected sites protected." : "Selected sites removed from Protected Sites.";
    }

    private void DeleteSelectedHistory()
    {
        var all = CheckedDomains(true);
        var domains = CheckedDomains(false);
        if (all.Count == 0)
        {
            MessageBox.Show("Select one or more sites first.", Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        if (domains.Count == 0)
        {
            MessageBox.Show("Every selected site is protected. Unprotect a site before deleting its history.", Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        string range = rangeCombo.SelectedItem?.ToString() ?? "All Time";
        var answer = MessageBox.Show(
            $"Delete selected Chrome HISTORY for {domains.Count} site(s) in the '{range}' view?\r\n\r\n" +
            "This does NOT delete cookies, saved passwords, autofill or bookmarks.\r\n\r\n" +
            "A backup of Chrome's History database will be made first.",
            "Confirm History Cleanup", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
        if (answer != DialogResult.Yes) return;

        if (Process.GetProcessesByName("chrome").Length > 0)
        {
            MessageBox.Show("Please CLOSE Google Chrome completely, then click Delete Selected History again.\r\n\r\nChrome is not force-closed so you do not lose open work.", Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        if (profileCombo.SelectedItem is not ChromeProfile profile) return;

        try
        {
            deleteButton.Enabled = false;
            statusLabel.Text = "Preparing safe history cleanup...";
            Refresh();

            var cutoff = CutoffDate();
            var domainSet = new HashSet<string>(domains, StringComparer.OrdinalIgnoreCase);
            var visitIds = new List<long>();
            var urlIds = new HashSet<long>();

            using (var conn = new SqliteConnection($"Data Source={profile.HistoryPath}"))
            {
                conn.Open();
                using var cmd = conn.CreateCommand();
                cmd.CommandText = "SELECT v.id, u.id, u.url, v.visit_time FROM visits v JOIN urls u ON u.id=v.url;";
                using var reader = cmd.ExecuteReader();
                while (reader.Read())
                {
                    long visitId = reader.GetInt64(0);
                    long urlId = reader.GetInt64(1);
                    string url = reader.GetString(2);
                    var when = ChromeTimeToLocal(reader.GetInt64(3));
                    if (when < cutoff) continue;
                    var domain = NormalizeDomain(url);
                    if (domain != null && domainSet.Contains(domain))
                    {
                        visitIds.Add(visitId);
                        urlIds.Add(urlId);
                    }
                }
            }

            if (visitIds.Count == 0)
            {
                MessageBox.Show("No matching history visits were found. Nothing was changed.", Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            string stamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
            string safeProfile = string.Concat(profile.Name.Select(c => char.IsLetterOrDigit(c) || c is '-' or '_' ? c : '_'));
            string backupPath = Path.Combine(BackupDir, $"{safeProfile}_History_{stamp}.bak");
            File.Copy(profile.HistoryPath, backupPath, true);

            using (var conn = new SqliteConnection($"Data Source={profile.HistoryPath}"))
            {
                conn.Open();
                using var tx = conn.BeginTransaction();
                bool hasVisitSource = TableExists(conn, tx, "visit_source");

                foreach (var chunk in Chunk(visitIds, 300))
                {
                    string ids = string.Join(',', chunk);
                    if (hasVisitSource) Execute(conn, tx, $"DELETE FROM visit_source WHERE id IN ({ids});");
                    Execute(conn, tx, $"DELETE FROM visits WHERE id IN ({ids});");
                }

                foreach (var chunk in Chunk(urlIds.ToList(), 300))
                {
                    string ids = string.Join(',', chunk);
                    Execute(conn, tx,
                        $"UPDATE urls SET " +
                        $"visit_count=(SELECT COUNT(*) FROM visits WHERE visits.url=urls.id), " +
                        $"last_visit_time=COALESCE((SELECT MAX(visit_time) FROM visits WHERE visits.url=urls.id),0) " +
                        $"WHERE id IN ({ids});");
                }
                tx.Commit();
            }

            statusLabel.Text = $"Cleanup complete: {visitIds.Count} history visit(s) removed. Login data was untouched.";
            ScanHistory();
            MessageBox.Show($"Done.\r\n\r\nRemoved {visitIds.Count} history visit(s).\r\nCookies and passwords were not touched.\r\n\r\nBackup:\r\n{backupPath}", Text, MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show("History cleanup did not complete.\r\n\r\n" + ex.Message + "\r\n\r\nBackups are stored in:\r\n" + BackupDir, Text, MessageBoxButtons.OK, MessageBoxIcon.Error);
            statusLabel.Text = "Cleanup failed; no intentional cookie/password cleanup was performed.";
        }
        finally
        {
            deleteButton.Enabled = true;
        }
    }

    private static bool TableExists(SqliteConnection conn, SqliteTransaction tx, string name)
    {
        using var cmd = conn.CreateCommand();
        cmd.Transaction = tx;
        cmd.CommandText = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=$name LIMIT 1;";
        cmd.Parameters.AddWithValue("$name", name);
        return cmd.ExecuteScalar() != null;
    }

    private static void Execute(SqliteConnection conn, SqliteTransaction tx, string sql)
    {
        using var cmd = conn.CreateCommand();
        cmd.Transaction = tx;
        cmd.CommandText = sql;
        cmd.ExecuteNonQuery();
    }

    private static IEnumerable<List<long>> Chunk(List<long> ids, int size)
    {
        for (int i = 0; i < ids.Count; i += size)
            yield return ids.GetRange(i, Math.Min(size, ids.Count - i));
    }

    private static DateTime ChromeTimeToLocal(long chromeMicros)
    {
        try { return DateTime.FromFileTimeUtc(checked(chromeMicros * 10)).ToLocalTime(); }
        catch { return DateTime.MinValue; }
    }

    private static string? NormalizeDomain(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) return null;
        if (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps) return null;
        string host = uri.Host.TrimEnd('.').ToLowerInvariant();
        if (host.StartsWith("www.")) host = host[4..];
        return host.Length == 0 ? null : host;
    }

    private static void CopyFileShared(string source, string destination)
    {
        using var input = new FileStream(source, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        using var output = new FileStream(destination, FileMode.Create, FileAccess.Write, FileShare.None);
        input.CopyTo(output);
    }

    private static void TryDelete(string path)
    {
        try { if (File.Exists(path)) File.Delete(path); } catch { }
    }

    private void LoadProtectedSites()
    {
        try
        {
            if (!File.Exists(ProtectedFile)) return;
            var items = JsonSerializer.Deserialize<List<string>>(File.ReadAllText(ProtectedFile)) ?? new();
            foreach (var item in items.Where(x => !string.IsNullOrWhiteSpace(x))) protectedSites.Add(item);
        }
        catch { }
    }

    private void SaveProtectedSites()
    {
        Directory.CreateDirectory(_settingsDir);
        File.WriteAllText(ProtectedFile, JsonSerializer.Serialize(protectedSites.OrderBy(x => x).ToList(), new JsonSerializerOptions { WriteIndented = true }));
    }
}
