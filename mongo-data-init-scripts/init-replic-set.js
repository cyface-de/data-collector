config = {
  	"_id" : "data",
  	"members" : [
  		{
  			"_id" : 0,
  			"host" : "mongo-data:27017"
  		}
  	]
  }

rs.initiate(config)
