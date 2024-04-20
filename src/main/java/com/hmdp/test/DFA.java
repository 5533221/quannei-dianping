package compile_2;

import java.util.*;
public class DFA {
	char initialState;
	char[] inputSymbols;
	char[] states;
	char[][] transitionTable;
	char[] finalStates;
	public DFA() {
		initialState = '1';
		inputSymbols = new char[] {'a','b','c','d'};
		states = new char[] { '1', '2', '3','4', '5', '6','7' };
		transitionTable = new char[][]{{'3','2',' ',' '},
				{'4','2',' ',' '},
				{' ','6','3','5'},
				{' ','7','3','5'},
				{'4',' ',' ',' '},
				{' ','6',' ',' '},
				{' ','6',' ',' '}};
		finalStates = new char[] { '6','7' };
	}
	private char transition(char currentState, char nextSymbol) {
		int m = -1, n = -1;
		for (int i = 0; i < states.length; i++) {
			if (currentState == states[i]) {
				m = i;
				break;
			}
		}
		for (int i = 0; i < inputSymbols.length; i++) {
			if (nextSymbol == inputSymbols[i]) {
				n = i;
				break;

			}
		}
		if (n == -1 || m == -1) {
			return '0';
		}
		return transitionTable[m][n];
	}
	public static void main(String[] args) {
		DFA dfa = new DFA();
		Scanner scanner = new Scanner(System.in);
		System.out.println("请输入字符串");
		String inputStr = scanner.nextLine();
		String inputStrings[] = inputStr.trim().split("//s");
		char currentState = dfa.initialState;
		for (int i = 0; i < inputStrings.length; i++) {
			for (int j = 0; j < inputStrings[i].length(); j++) {
				currentState = dfa.transition(currentState, inputStrings[i].charAt(j));
				if(currentState=='0'){
					System.out.println("no");
					System.exit(0);
				}
			}
			boolean flag=false;
			for(int k=0;k<dfa.finalStates.length;k++){
				if(currentState==dfa.finalStates[k]){
					flag=true;
					break;
				}
			}
			if(flag){
				System.out.println("yes");
			}else{
				System.out.println("no");
			}
		}
	}
}
