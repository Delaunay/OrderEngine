

run/All: run/Client run/SampleTrader run/MarketData run/Router run/OrderManager

run/Client:
	cd bin/; java StandAloneClient &

run/SampleTrader:
	cd bin/; java StandAloneTrader.class &

run/MarketData:
	cd bin/; java StandAloneMarket.class &

run/Router:
	cd bin/; java StandAloneRouter.class &

run/OrderManager:
	cd bin/; java OrderManager