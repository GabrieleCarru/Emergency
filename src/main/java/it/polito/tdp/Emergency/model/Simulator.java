package it.polito.tdp.Emergency.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import it.polito.tdp.Emergency.model.Event.EventType;
import it.polito.tdp.Emergency.model.Paziente.CodiceColore;

public class Simulator {

	// PARAMETRI DI SIMULAZIONE
	private int NS = 5; 	// Numero studi medici
	
	private int NP = 150; 	// Numero di pazienti
	private Duration T_ARRIVAL = Duration.ofMinutes(5); 	// Intervallo tra i pazienti
	
	private final Duration DURATION_TRIAGE = Duration.ofMinutes(5);
	private final Duration DURATION_WHITE = Duration.ofMinutes(10);
	private final Duration DURATION_YELLOW = Duration.ofMinutes(15);
	private final Duration DURATION_RED = Duration.ofMinutes(30);
	
	private final Duration TIMEOUT_WHITE = Duration.ofMinutes(90);
	private final Duration TIMEOUT_YELLOW = Duration.ofMinutes(30);
	private final Duration TIMEOUT_RED = Duration.ofMinutes(60);
	
	private final LocalTime oraInizio = LocalTime.of(8, 00);
	private final LocalTime oraFine = LocalTime.of(20, 00);
	
	private final Duration TICK_TIME = Duration.ofMinutes(5);
	
	// OUTPUT DA CALCOLARE
	private int pazientiTot;
	private int pazientiDimessi;
	private int pazientiAbbandonano;
	private int pazientiMorti;
	
	// STATO DEL SISTEMA
	private List<Paziente> pazienti;
	private CodiceColore coloreAssegnato;
	private PriorityQueue<Paziente> attesa; // struttura dati ausiliare
	private int studiLiberi;
	
	// CODA DEGLI EVENTI
	private PriorityQueue<Event> queue;
	
	
	// INIZIALIZZAZIONE
	public void init() {
		//Costruire tutte le strutture dati e azzerare tutti i contatori
		this.queue = new PriorityQueue<Event>(); // Coda degli eventi
		this.pazienti = new ArrayList<Paziente>();
		this.attesa = new PriorityQueue<Paziente>();
		
		this.pazientiTot = 0;
		this.pazientiDimessi = 0;
		this.pazientiAbbandonano = 0;
		this.pazientiMorti = 0;
		
		this.studiLiberi = this.NS;
		
		this.coloreAssegnato = CodiceColore.WHITE;
		
		// generare eventi iniziali
		int nPaz = 0;
		LocalTime oraArrivo = this.oraInizio;
		
		while(nPaz < this.NP && oraArrivo.isBefore(this.oraFine)) {
			Paziente p = new Paziente(oraArrivo, CodiceColore.UNKNOWN);
			this.pazienti.add(p);
			Event e = new Event(oraArrivo, EventType.ARRIVAL, p);
			queue.add(e);
			
			nPaz++;
			oraArrivo = oraArrivo.plus(T_ARRIVAL);
		}
	}
	
	
	// ESECUZIONE
	public void run() {
		while(!this.queue.isEmpty()) {
			Event e = this.queue.poll();
			System.out.println(e);
			processEvent(e);
		}
	} 
	
	private void processEvent(Event e) {
		
		Paziente p = e.getPaziente();
		
		switch(e.getType()) {
		case ARRIVAL:
			//Arriva un paziente: tra 5 minuti sarà finito il traige
			queue.add(new Event(e.getTime().plus(DURATION_TRIAGE), EventType.TRIAGE, p));
			this.pazientiTot++;
			break;
			
		case TRIAGE:
			// Assegnare codice colore 
			p.setColore(nuovoCodiceColore());
			
			// Mettere il lista d'attesa 
			attesa.add(p);
			
			// Schedulare timeout
			if(p.getColore() == CodiceColore.WHITE)
				queue.add(new Event(e.getTime().plus(TIMEOUT_WHITE), EventType.TIMEOUT, p));
			else if(p.getColore() == CodiceColore.YELLOW)
				queue.add(new Event(e.getTime().plus(TIMEOUT_YELLOW), EventType.TIMEOUT, p));
			else if(p.getColore() == CodiceColore.RED)
				queue.add(new Event(e.getTime().plus(TIMEOUT_RED), EventType.TIMEOUT, p));
			break;
			
		case FREE_STUDIO:
			Paziente prossimo = attesa.poll();
			if(prossimo != null) {
				
				// lo faccio entrare
				this.studiLiberi--;
				
				if(prossimo.getColore() == CodiceColore.WHITE)
					queue.add(new Event(e.getTime().plus(DURATION_WHITE), 
							EventType.TREATED, prossimo));
				else if(prossimo.getColore() == CodiceColore.YELLOW)
					queue.add(new Event(e.getTime().plus(DURATION_YELLOW), 
							EventType.TREATED, prossimo));
				else if(prossimo.getColore() == CodiceColore.RED)
					queue.add(new Event(e.getTime().plus(DURATION_RED), 
							EventType.TREATED, prossimo));
			}
			break;
			
		case TREATED:
			// libero lo studio 
			this.studiLiberi++;
			
			this.pazientiDimessi++;
			queue.add(new Event(e.getTime(), EventType.FREE_STUDIO, null));
			break;
			
		case TIMEOUT:
			//esci dalla lista d'attesa
			attesa.remove(p);
			
			switch(p.getColore()) {
			case WHITE:
				// Va a casa
				this.pazientiAbbandonano++;
				
				break;
			case YELLOW:
				// Diventa RED
				p.setColore(CodiceColore.RED);
				attesa.add(p);
				queue.add(new Event(e.getTime().plus(DURATION_RED), EventType.TIMEOUT, p));
				break;
			case RED:
				// Muore
				this.pazientiMorti++;
				break;
			}
			break;
			
		case TICK:
			if(this.studiLiberi > 0) {
				queue.add(new Event(e.getTime(), EventType.FREE_STUDIO, null));
			}
			this.queue.add(new Event(e.getTime().plus(this.TICK_TIME), EventType.TICK, null));
			break;
		}
	}
	
	
	private CodiceColore nuovoCodiceColore() {
		// Assegna CICLICAMENTE un colore diverso
		
		CodiceColore nuovo = coloreAssegnato;
		
		if(coloreAssegnato == CodiceColore.WHITE)
			coloreAssegnato = CodiceColore.YELLOW;
		else if(coloreAssegnato == CodiceColore.YELLOW)
			coloreAssegnato = CodiceColore.RED;
		else 
			coloreAssegnato = CodiceColore.WHITE;
		return null;
	}


	// Getter e Setter
	public int getNS() {
		return NS;
	}
	public void setNS(int nS) {
		NS = nS;
	}
	public int getNP() {
		return NP;
	}
	public void setNP(int nP) {
		NP = nP;
	}
	public Duration getT_ARRIVAL() {
		return T_ARRIVAL;
	}
	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}
}
