

run/All: run/Client run/Trader run/MarketData run/Router run/OrderManager

run/Client:
	cd bin/; java StandAloneClient &

run/Trader:
	cd bin/; java StandAloneTrader.class &

run/MarketData:
	cd bin/; java StandAloneMarket.class &

run/Router:
	cd bin/; java StandAloneRouter.class &

run/OrderManager:
	cd bin/; java OrderManager