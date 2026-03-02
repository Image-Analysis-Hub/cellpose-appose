from cellpose import models

model = models.CellposeModel(gpu=True)

print("we loaded cellpose v3 default model")