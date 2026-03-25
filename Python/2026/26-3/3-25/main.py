# Git仓库同步工具,AI生成
import os
import subprocess
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext

CONFIG_FILE = "repos.txt"

class GitSyncTool:
    def __init__(self, root):
        self.root = root
        self.root.title("Git仓库同步工具")
        self.root.geometry("700x550")
        
        self.repos = []
        self.load_repos()
        
        self.setup_ui()
        
    def load_repos(self):
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                self.repos = [line.strip() for line in f if line.strip()]
                
    def save_repos(self):
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            for repo in self.repos:
                f.write(repo + "\n")
                
    def setup_ui(self):
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        title_label = ttk.Label(main_frame, text="Git仓库同步工具", font=("微软雅黑", 16, "bold"))
        title_label.pack(pady=10)
        
        repo_frame = ttk.LabelFrame(main_frame, text="仓库列表", padding="5")
        repo_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.repo_listbox = tk.Listbox(repo_frame, height=8)
        self.repo_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.update_listbox()
        
        scrollbar = ttk.Scrollbar(repo_frame, orient=tk.VERTICAL, command=self.repo_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.repo_listbox.config(yscrollcommand=scrollbar.set)
        
        btn_frame = ttk.Frame(main_frame)
        btn_frame.pack(fill=tk.X, pady=5)
        
        ttk.Button(btn_frame, text="添加仓库", command=self.add_repo).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="删除选中", command=self.delete_repo).pack(side=tk.LEFT, padx=5)
        
        action_frame = ttk.LabelFrame(main_frame, text="同步操作", padding="5")
        action_frame.pack(fill=tk.X, pady=5)
        
        ttk.Button(action_frame, text="Git Pull", command=self.git_pull, width=15).pack(side=tk.LEFT, padx=10)
        ttk.Button(action_frame, text="Git Push", command=self.git_push, width=15).pack(side=tk.LEFT, padx=10)
        
        log_frame = ttk.LabelFrame(main_frame, text="执行日志", padding="5")
        log_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.log_text = scrolledtext.ScrolledText(log_frame, height=10, font=("Consolas", 9))
        self.log_text.pack(fill=tk.BOTH, expand=True)
        
    def update_listbox(self):
        self.repo_listbox.delete(0, tk.END)
        for repo in self.repos:
            self.repo_listbox.insert(tk.END, repo)
            
    def add_repo(self):
        path = filedialog.askdirectory(title="选择Git仓库文件夹")
        if path and path not in self.repos:
            if os.path.exists(os.path.join(path, ".git")):
                self.repos.append(path)
                self.save_repos()
                self.update_listbox()
                self.log(f"已添加仓库: {path}")
            else:
                messagebox.showwarning("警告", "所选文件夹不是Git仓库")
        elif path in self.repos:
            messagebox.showinfo("提示", "该仓库已在列表中")
            
    def delete_repo(self):
        selection = self.repo_listbox.curselection()
        if selection:
            index = selection[0]
            removed = self.repos.pop(index)
            self.save_repos()
            self.update_listbox()
            self.log(f"已删除仓库: {removed}")
            
    def log(self, message):
        self.log_text.insert(tk.END, message + "\n")
        self.log_text.see(tk.END)
        
    def run_git_command(self, repo_path, command):
        try:
            result = subprocess.run(
                command,
                cwd=repo_path,
                shell=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace"
            )
            return result.returncode, result.stdout, result.stderr
        except Exception as e:
            return -1, "", str(e)
            
    def git_pull(self):
        if not self.repos:
            messagebox.showinfo("提示", "请先添加仓库")
            return
            
        self.log("=" * 40)
        self.log("开始执行 Git Pull...")
        self.log("=" * 40)
        
        success_count = 0
        fail_count = 0
        
        for repo in self.repos:
            self.log(f"\n[{repo}]")
            returncode, stdout, stderr = self.run_git_command(repo, "git pull")
            
            if returncode == 0:
                self.log(f"✓ Pull成功")
                if stdout.strip():
                    self.log(f"  {stdout.strip()}")
                success_count += 1
            else:
                self.log(f"✗ Pull失败")
                if stdout.strip():
                    self.log(f"  {stdout.strip()}")
                if stderr.strip():
                    self.log(f"  错误: {stderr.strip()}")
                fail_count += 1
                
        self.log(f"\n完成! 成功: {success_count}, 失败: {fail_count}")
        messagebox.showinfo("完成", f"Git Pull执行完成\n成功: {success_count}, 失败: {fail_count}")
        
    def git_push(self):
        if not self.repos:
            messagebox.showinfo("提示", "请先添加仓库")
            return
            
        result = messagebox.askyesno("确认", "确定要执行Git Push吗?\n请确保本地修改已提交!")
        if not result:
            return
            
        self.log("=" * 40)
        self.log("开始执行 Git Push...")
        self.log("=" * 40)
        
        success_count = 0
        fail_count = 0
        
        for repo in self.repos:
            self.log(f"\n[{repo}]")
            returncode, stdout, stderr = self.run_git_command(repo, "git push")
            
            if returncode == 0:
                self.log(f"✓ Push成功")
                if stdout.strip():
                    self.log(f"  {stdout.strip()}")
                success_count += 1
            else:
                self.log(f"✗ Push失败")
                if stdout.strip():
                    self.log(f"  {stdout.strip()}")
                if stderr.strip():
                    self.log(f"  错误: {stderr.strip()}")
                fail_count += 1
                
        self.log(f"\n完成! 成功: {success_count}, 失败: {fail_count}")
        messagebox.showinfo("完成", f"Git Push执行完成\n成功: {success_count}, 失败: {fail_count}")

def main():
    root = tk.Tk()
    app = GitSyncTool(root)
    root.mainloop()

if __name__ == "__main__":
    main()