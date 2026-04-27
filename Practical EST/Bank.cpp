#include <iostream>
#include <string>
#include <vector>

using namespace std;

class BankAccount {
private:
	int accountNumber;
	string accountHolder;
	double balance;

public:
	BankAccount(int accNo, string name, double initialBalance)
		: accountNumber(accNo), accountHolder(name), balance(initialBalance) {}

	int getAccountNumber() const {
		return accountNumber;
	}

	void deposit(double amount) {
		if (amount > 0) {
			balance += amount;
			cout << "Deposit successful.\n";
		} else {
			cout << "Invalid deposit amount.\n";
		}
	}

	void withdraw(double amount) {
		if (amount > 0 && amount <= balance) {
			balance -= amount;
			cout << "Withdrawal successful.\n";
		} else {
			cout << "Invalid withdrawal amount or insufficient balance.\n";
		}
	}

	void display() const {
		cout << "\nAccount Number: " << accountNumber << '\n';
		cout << "Account Holder : " << accountHolder << '\n';
		cout << "Balance        : " << balance << '\n';
	}
};

class Bank {
private:
	vector<BankAccount> accounts;

	BankAccount* findAccount(int accountNumber) {
		for (auto &account : accounts) {
			if (account.getAccountNumber() == accountNumber) {
				return &account;
			}
		}
		return nullptr;
	}

public:
	void createAccount() {
		int accNo;
		string name;
		double balance;

		cout << "Enter account number: ";
		cin >> accNo;
		cout << "Enter account holder name: ";
		cin.ignore();
		getline(cin, name);
		cout << "Enter opening balance: ";
		cin >> balance;

		accounts.push_back(BankAccount(accNo, name, balance));
		cout << "Account created successfully.\n";
	}

	void depositMoney() {
		int accNo;
		double amount;

		cout << "Enter account number: ";
		cin >> accNo;
		cout << "Enter deposit amount: ";
		cin >> amount;

		BankAccount* account = findAccount(accNo);
		if (account) {
			account->deposit(amount);
		} else {
			cout << "Account not found.\n";
		}
	}

	void withdrawMoney() {
		int accNo;
		double amount;

		cout << "Enter account number: ";
		cin >> accNo;
		cout << "Enter withdrawal amount: ";
		cin >> amount;

		BankAccount* account = findAccount(accNo);
		if (account) {
			account->withdraw(amount);
		} else {
			cout << "Account not found.\n";
		}
	}

	void displayAccount() {
		int accNo;

		cout << "Enter account number: ";
		cin >> accNo;

		BankAccount* account = findAccount(accNo);
		if (account) {
			account->display();
		} else {
			cout << "Account not found.\n";
		}
	}
};

int main() {
	Bank bank;
	int choice;

	do {
		cout << "\n===== Simple Banking Application =====\n";
		cout << "1. Create Account\n";
		cout << "2. Deposit Money\n";
		cout << "3. Withdraw Money\n";
		cout << "4. Display Account Details\n";
		cout << "5. Exit\n";
		cout << "Enter your choice: ";
		cin >> choice;

		switch (choice) {
			case 1:
				bank.createAccount();
				break;
			case 2:
				bank.depositMoney();
				break;
			case 3:
				bank.withdrawMoney();
				break;
			case 4:
				bank.displayAccount();
				break;
			case 5:
				cout << "Exiting...\n";
				break;
			default:
				cout << "Invalid choice.\n";
		}
	} while (choice != 5);

	return 0;
}
